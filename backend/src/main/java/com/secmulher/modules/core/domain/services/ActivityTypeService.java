package com.secmulher.modules.core.domain.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.secmulher.modules.core.domain.access.AccessControlService;
import com.secmulher.modules.core.domain.dtos.ActivityTypeDtos.ActivityTypeRequest;
import com.secmulher.modules.core.domain.dtos.ActivityTypeDtos.ActivityTypeResponse;
import com.secmulher.modules.core.domain.exceptions.BusinessException;
import com.secmulher.modules.core.domain.exceptions.ConflictException;
import com.secmulher.modules.core.domain.exceptions.ForbiddenException;
import com.secmulher.modules.core.domain.exceptions.NotFoundException;
import com.secmulher.modules.core.infra.entities.Category;
import com.secmulher.modules.core.infra.entities.CategoryActivityType;
import com.secmulher.modules.core.infra.entities.Permission;
import com.secmulher.modules.core.infra.repositories.ActivityLedgerRepository;
import com.secmulher.modules.core.infra.repositories.CategoryActivityTypeRepository;
import com.secmulher.modules.core.infra.repositories.CategoryRepository;
import com.secmulher.modules.core.infra.repositories.PermissionRepository;

// Tipos de registro manual da linha do tempo, configurados por categoria (nativa ou dinâmica).
// Leitura: quem pode ler a categoria; escrita: só administradores.
@Service
public class ActivityTypeService {
    // Criados em toda categoria nova (e, pela migração do init.sql, nas existentes)
    record DefaultType(String key, String label) {}

    static final List<DefaultType> DEFAULTS = List.of(
        new DefaultType("triagem", "Triagem"),
        new DefaultType("atendimento", "Atendimento"),
        new DefaultType("acompanhamento", "Acompanhamento"),
        new DefaultType("anotacao", "Anotação"));

    final CategoryActivityTypeRepository activityTypeRepository;
    final CategoryRepository categoryRepository;
    final ActivityLedgerRepository activityLedgerRepository;
    final PermissionRepository permissionRepository;
    final AccessControlService accessControl;

    public ActivityTypeService(CategoryActivityTypeRepository activityTypeRepository,
            CategoryRepository categoryRepository, ActivityLedgerRepository activityLedgerRepository,
            PermissionRepository permissionRepository, AccessControlService accessControl) {
        this.activityTypeRepository = activityTypeRepository;
        this.categoryRepository = categoryRepository;
        this.activityLedgerRepository = activityLedgerRepository;
        this.permissionRepository = permissionRepository;
        this.accessControl = accessControl;
    }

    private Category getCategory(UUID categoryId) {
        return categoryRepository.findById(categoryId)
            .orElseThrow(() -> new NotFoundException("Categoria não encontrada: " + categoryId));
    }

    private CategoryActivityType getType(UUID categoryId, UUID typeId) {
        return activityTypeRepository.findById(typeId)
            .filter(type -> type.getCategory().getId().equals(categoryId))
            .orElseThrow(() -> new NotFoundException("Tipo de registro não encontrado nesta categoria: " + typeId));
    }

    // Tipos ativos por padrão; os inativos só para administradores
    @Transactional(readOnly = true)
    public List<ActivityTypeResponse> findAll(UUID categoryId, boolean includeInactive) {
        getCategory(categoryId);
        accessControl.forCategory(categoryId).requireRead();
        if (includeInactive && !accessControl.isAdmin()) {
            throw new ForbiddenException("Apenas administradores podem listar tipos inativos");
        }
        List<CategoryActivityType> types = includeInactive
            ? activityTypeRepository.findByCategoryIdOrderByPositionAscLabelAsc(categoryId)
            : activityTypeRepository.findByCategoryIdAndIsActiveTrueOrderByPositionAscLabelAsc(categoryId);
        return types.stream().map(ActivityTypeResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryActivityType> getActiveTypes(UUID categoryId) {
        return activityTypeRepository.findByCategoryIdAndIsActiveTrueOrderByPositionAscLabelAsc(categoryId);
    }

    @Transactional(readOnly = true)
    public List<String> getKeys(UUID categoryId) {
        return activityTypeRepository.findByCategoryIdOrderByPositionAscLabelAsc(categoryId).stream()
            .map(CategoryActivityType::getKey).toList();
    }

    @Transactional
    public ActivityTypeResponse create(UUID categoryId, ActivityTypeRequest request) {
        accessControl.requireAdmin();
        Category category = getCategory(categoryId);
        if (activityTypeRepository.existsByCategoryIdAndKey(categoryId, request.key())) {
            throw new ConflictException("Já existe um tipo de registro com a chave '" + request.key() + "' nesta categoria");
        }

        CategoryActivityType type = activityTypeRepository.saveAndFlush(CategoryActivityType.builder()
            .category(category)
            .key(request.key())
            .label(request.label())
            .description(request.description())
            .color(request.color())
            .position(Objects.requireNonNullElse(request.position(), 0))
            .isActive(Objects.requireNonNullElse(request.isActive(), true))
            .build());
        return ActivityTypeResponse.from(type);
    }

    @Transactional
    public ActivityTypeResponse update(UUID categoryId, UUID typeId, ActivityTypeRequest request) {
        accessControl.requireAdmin();
        CategoryActivityType type = getType(categoryId, typeId);
        if (!type.getKey().equals(request.key())) {
            throw new BusinessException("A chave de um tipo de registro não pode ser alterada; crie um novo tipo");
        }

        // Renomear não altera o histórico: o ledger guarda o nome do momento do registro
        type.setLabel(request.label());
        type.setDescription(request.description());
        type.setColor(request.color());
        if (request.position() != null) {
            type.setPosition(request.position());
        }
        if (request.isActive() != null) {
            type.setIsActive(request.isActive());
        }
        return ActivityTypeResponse.from(activityTypeRepository.saveAndFlush(type));
    }

    // Tipo já usado no histórico não pode sumir (a auditoria aponta para ele): só desativar
    @Transactional
    public void delete(UUID categoryId, UUID typeId) {
        accessControl.requireAdmin();
        CategoryActivityType type = getType(categoryId, typeId);
        if (activityLedgerRepository.existsByActivityTypeId(typeId)) {
            throw new ConflictException("O tipo '" + type.getLabel() + "' já foi usado na linha do tempo e não pode "
                + "ser excluído. Desative-o com isActive: false");
        }

        // Tira a key das permissões que a restringiam, para não valer para um tipo novo com a mesma key
        for (Permission permission : permissionRepository.findByCategoryId(categoryId)) {
            List<String> allowed = permission.getAllowedActivityTypes();
            if (allowed != null && allowed.contains(type.getKey())) {
                List<String> remaining = new ArrayList<>(allowed);
                remaining.remove(type.getKey());
                permission.setAllowedActivityTypes(remaining);
            }
        }
        activityTypeRepository.delete(type);
    }

    @Transactional(readOnly = true)
    public boolean isUsedInCategory(UUID categoryId) {
        return activityLedgerRepository.existsByActivityType_Category_Id(categoryId);
    }

    // Chamado ao criar uma categoria (a permissão de admin já foi checada por quem chama)
    @Transactional
    public void createDefaults(Category category) {
        for (int i = 0; i < DEFAULTS.size(); i++) {
            DefaultType def = DEFAULTS.get(i);
            activityTypeRepository.save(CategoryActivityType.builder()
                .category(category).key(def.key()).label(def.label()).position(i).isActive(true).build());
        }
    }
}
