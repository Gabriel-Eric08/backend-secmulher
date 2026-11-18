package com.secmulher.modules.core.domain.services;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.secmulher.modules.core.domain.access.AccessControlService;
import com.secmulher.modules.core.domain.dtos.PermissionDtos.PermissionRequest;
import com.secmulher.modules.core.domain.dtos.PermissionDtos.PermissionResponse;
import com.secmulher.modules.core.domain.exceptions.ConflictException;
import com.secmulher.modules.core.domain.exceptions.FieldValidationException;
import com.secmulher.modules.core.domain.exceptions.NotFoundException;
import com.secmulher.modules.core.infra.entities.Permission;
import com.secmulher.modules.core.infra.entities.enums.ConfidentialityLevel;
import com.secmulher.modules.core.infra.repositories.PermissionRepository;

// A matriz de permissões é gerida exclusivamente por administradores
@Service
public class PermissionService {
    final PermissionRepository permissionRepository;
    final UserService userService;
    final CategoryService categoryService;
    final AccessControlService accessControl;
    final ActivityTypeService activityTypeService;

    public PermissionService(PermissionRepository permissionRepository, UserService userService,
            CategoryService categoryService, AccessControlService accessControl,
            ActivityTypeService activityTypeService) {
        this.permissionRepository = permissionRepository;
        this.userService = userService;
        this.categoryService = categoryService;
        this.accessControl = accessControl;
        this.activityTypeService = activityTypeService;
    }

    private Permission getPermissionById(UUID id) {
        return permissionRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Permissão não encontrada: " + id));
    }

    // [] (ou null na criação) = sem restrição, guardado como null; as keys precisam existir na categoria
    private List<String> normalizeActivityTypes(UUID categoryId, List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return null;
        }
        List<String> distinct = keys.stream().distinct().toList();
        List<String> existing = activityTypeService.getKeys(categoryId);
        List<String> unknown = distinct.stream().filter(key -> !existing.contains(key)).toList();
        if (!unknown.isEmpty()) {
            throw new FieldValidationException("Dados inválidos", Map.of("allowedActivityTypes",
                "tipos de registro inexistentes nesta categoria: " + String.join(", ", unknown)));
        }
        return distinct;
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> findAll(UUID userId) {
        accessControl.requireAdmin();
        List<Permission> permissions = userId != null
            ? permissionRepository.findByUserId(userId)
            : permissionRepository.findAll();
        return permissions.stream().map(PermissionResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public PermissionResponse findById(UUID id) {
        accessControl.requireAdmin();
        return PermissionResponse.from(getPermissionById(id));
    }

    @Transactional
    public PermissionResponse create(PermissionRequest request) {
        accessControl.requireAdmin();
        if (permissionRepository.existsByUserIdAndCategoryId(request.userId(), request.categoryId())) {
            throw new ConflictException("O usuário já possui permissão para esta categoria");
        }

        Permission permission = Permission.builder()
            .user(userService.getUserById(request.userId()))
            .category(categoryService.getCategoryById(request.categoryId()))
            .canRead(Objects.requireNonNullElse(request.canRead(), false))
            .canCreate(Objects.requireNonNullElse(request.canCreate(), false))
            .canUpdate(Objects.requireNonNullElse(request.canUpdate(), false))
            .canDelete(Objects.requireNonNullElse(request.canDelete(), false))
            .allowedColumns(Objects.requireNonNullElse(request.allowedColumns(), new HashMap<>()))
            .maxConfidentiality(Objects.requireNonNullElse(request.maxConfidentiality(), ConfidentialityLevel.INTERNAL))
            .allowedActivityTypes(normalizeActivityTypes(request.categoryId(), request.allowedActivityTypes()))
            .build();

        return PermissionResponse.from(permissionRepository.saveAndFlush(permission));
    }

    @Transactional
    public PermissionResponse update(UUID id, PermissionRequest request) {
        accessControl.requireAdmin();
        Permission permission = getPermissionById(id);

        boolean targetChanged = !permission.getUser().getId().equals(request.userId())
            || !permission.getCategory().getId().equals(request.categoryId());
        if (targetChanged) {
            if (permissionRepository.existsByUserIdAndCategoryId(request.userId(), request.categoryId())) {
                throw new ConflictException("O usuário já possui permissão para esta categoria");
            }
            permission.setUser(userService.getUserById(request.userId()));
            permission.setCategory(categoryService.getCategoryById(request.categoryId()));
        }

        if (request.canRead() != null) {
            permission.setCanRead(request.canRead());
        }
        if (request.canCreate() != null) {
            permission.setCanCreate(request.canCreate());
        }
        if (request.canUpdate() != null) {
            permission.setCanUpdate(request.canUpdate());
        }
        if (request.canDelete() != null) {
            permission.setCanDelete(request.canDelete());
        }
        if (request.allowedColumns() != null) {
            permission.setAllowedColumns(request.allowedColumns());
        }
        if (request.maxConfidentiality() != null) {
            permission.setMaxConfidentiality(request.maxConfidentiality());
        }
        // null mantém; [] remove a restrição. Mudando de categoria, as keys antigas são revalidadas na nova.
        if (request.allowedActivityTypes() != null) {
            permission.setAllowedActivityTypes(normalizeActivityTypes(request.categoryId(), request.allowedActivityTypes()));
        } else if (targetChanged && permission.getAllowedActivityTypes() != null) {
            permission.setAllowedActivityTypes(
                normalizeActivityTypes(request.categoryId(), permission.getAllowedActivityTypes()));
        }

        return PermissionResponse.from(permissionRepository.saveAndFlush(permission));
    }

    @Transactional
    public void delete(UUID id) {
        accessControl.requireAdmin();
        permissionRepository.delete(getPermissionById(id));
    }
}
