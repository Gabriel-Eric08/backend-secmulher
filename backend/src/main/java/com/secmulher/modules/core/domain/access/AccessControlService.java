package com.secmulher.modules.core.domain.access;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.secmulher.modules.core.domain.enums.RecordType;
import com.secmulher.modules.core.domain.exceptions.ForbiddenException;
import com.secmulher.modules.core.domain.exceptions.NotFoundException;
import com.secmulher.modules.core.domain.exceptions.UnauthorizedException;
import com.secmulher.modules.core.infra.entities.Category;
import com.secmulher.modules.core.infra.entities.User;
import com.secmulher.modules.core.infra.repositories.CategoryRepository;
import com.secmulher.modules.core.infra.repositories.DynamicEntityRepository;
import com.secmulher.modules.core.infra.repositories.PermissionRepository;
import com.secmulher.modules.core.infra.security.CurrentUserProvider;

// Ponto único das regras de autorização. Administradores globais têm acesso total;
// os demais usuários dependem da matriz de permissões por categoria.
@Service
public class AccessControlService {
    final CurrentUserProvider currentUserProvider;
    final PermissionRepository permissionRepository;
    final CategoryRepository categoryRepository;
    final DynamicEntityRepository dynamicEntityRepository;

    public AccessControlService(CurrentUserProvider currentUserProvider, PermissionRepository permissionRepository,
            CategoryRepository categoryRepository, DynamicEntityRepository dynamicEntityRepository) {
        this.currentUserProvider = currentUserProvider;
        this.permissionRepository = permissionRepository;
        this.categoryRepository = categoryRepository;
        this.dynamicEntityRepository = dynamicEntityRepository;
    }

    public User requireUser() {
        return currentUserProvider.getCurrentUser()
            .orElseThrow(() -> new UnauthorizedException("Autenticação necessária"));
    }

    public boolean isAdmin() {
        return Boolean.TRUE.equals(requireUser().getIsGlobalAdmin());
    }

    public void requireAdmin() {
        if (!isAdmin()) {
            throw new ForbiddenException("Apenas administradores podem realizar esta operação");
        }
    }

    public void requireAdminOrSelf(UUID userId) {
        User user = requireUser();
        if (!Boolean.TRUE.equals(user.getIsGlobalAdmin()) && !user.getId().equals(userId)) {
            throw new ForbiddenException("Sem permissão para acessar dados de outro usuário");
        }
    }

    @Transactional(readOnly = true)
    public CategoryAccess forCategory(UUID categoryId) {
        User user = requireUser();
        if (Boolean.TRUE.equals(user.getIsGlobalAdmin())) {
            return CategoryAccess.full(categoryId);
        }
        return permissionRepository.findByUserIdAndCategoryId(user.getId(), categoryId)
            .map(CategoryAccess::from)
            .orElseGet(() -> CategoryAccess.none(categoryId));
    }

    // Registros de tabelas nativas pertencem à categoria cuja table_reference aponta para a tabela
    @Transactional(readOnly = true)
    public CategoryAccess forNativeTable(String tableName) {
        UUID categoryId = categoryRepository.findFirstByTableReferenceOrderByCreatedAtAsc(tableName).map(Category::getId).orElse(null);
        if (isAdmin()) {
            return CategoryAccess.full(categoryId);
        }
        return categoryId != null ? forCategory(categoryId) : CategoryAccess.none(null);
    }

    @Transactional(readOnly = true)
    public CategoryAccess forRecord(RecordType type, UUID recordId) {
        return switch (type) {
            case CLIENT -> forNativeTable(type.getTableName());
            case DYNAMIC_ENTITY -> dynamicEntityRepository.findById(recordId)
                .map(entity -> forCategory(entity.getCategory().getId()))
                .orElseThrow(() -> new NotFoundException("Registro não encontrado: " + type.getPath() + "/" + recordId));
        };
    }

    // Categoria a que o registro pertence; null para clientes quando não há categoria nativa 'clients'
    @Transactional(readOnly = true)
    public UUID categoryIdForRecord(RecordType type, UUID recordId) {
        return switch (type) {
            case CLIENT -> categoryRepository.findFirstByTableReferenceOrderByCreatedAtAsc(type.getTableName())
                .map(Category::getId).orElse(null);
            case DYNAMIC_ENTITY -> dynamicEntityRepository.findById(recordId)
                .map(entity -> entity.getCategory().getId())
                .orElseThrow(() -> new NotFoundException("Registro não encontrado: " + type.getPath() + "/" + recordId));
        };
    }

    // Categorias que o usuário (não administrador) pode ler
    @Transactional(readOnly = true)
    public Set<UUID> readableCategoryIds() {
        return permissionRepository.findByUserId(requireUser().getId()).stream()
            .filter(permission -> Boolean.TRUE.equals(permission.getCanRead()))
            .map(permission -> permission.getCategory().getId())
            .collect(Collectors.toSet());
    }
}
