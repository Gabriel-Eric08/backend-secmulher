package com.secmulher.modules.core.domain.services;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.secmulher.modules.core.domain.access.AccessControlService;
import com.secmulher.modules.core.domain.access.CategoryAccess;
import com.secmulher.modules.core.domain.dtos.DynamicEntityDtos.DynamicEntityRequest;
import com.secmulher.modules.core.domain.dtos.DynamicEntityDtos.DynamicEntityResponse;
import com.secmulher.modules.core.domain.dtos.DynamicEntityDtos.DynamicEntitySearchRequest;
import com.secmulher.modules.core.domain.enums.RecordType;
import com.secmulher.modules.core.domain.exceptions.BusinessException;
import com.secmulher.modules.core.domain.exceptions.ForbiddenException;
import com.secmulher.modules.core.domain.exceptions.NotFoundException;
import com.secmulher.modules.core.infra.entities.Category;
import com.secmulher.modules.core.infra.entities.DynamicEntity;
import com.secmulher.modules.core.infra.entities.enums.ActivityType;
import com.secmulher.modules.core.infra.repositories.DynamicEntityRepository;
import com.secmulher.modules.core.infra.repositories.DynamicEntitySearchRepository;

// Regras de coluna: na criação, campos sem permissão de escrita não podem ser enviados;
// na edição, alterá-los é recusado (reenviar o valor atual é aceito) e os ocultos são preservados.
@Service
public class DynamicEntityService {
    final DynamicEntityRepository dynamicEntityRepository;
    final CategoryService categoryService;
    final CategoryFieldService categoryFieldService;
    final ActivityService activityService;
    final FileService fileService;
    final AccessControlService accessControl;
    final DynamicEntitySearchRepository dynamicEntitySearchRepository;

    public DynamicEntityService(DynamicEntityRepository dynamicEntityRepository, CategoryService categoryService,
            CategoryFieldService categoryFieldService, ActivityService activityService, FileService fileService,
            AccessControlService accessControl, DynamicEntitySearchRepository dynamicEntitySearchRepository) {
        this.dynamicEntityRepository = dynamicEntityRepository;
        this.categoryService = categoryService;
        this.categoryFieldService = categoryFieldService;
        this.activityService = activityService;
        this.fileService = fileService;
        this.accessControl = accessControl;
        this.dynamicEntitySearchRepository = dynamicEntitySearchRepository;
    }

    private DynamicEntity getDynamicEntityById(UUID id) {
        return dynamicEntityRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Registro não encontrado: " + id));
    }

    // Só categorias do tipo 'dynamic' e ativas recebem registros em dynamic_entities
    private Category getDynamicCategory(UUID categoryId) {
        Category category = categoryService.getCategoryById(categoryId);
        if (!"dynamic".equals(category.getEntityType())) {
            throw new BusinessException("A categoria " + category.getName() + " não é dinâmica");
        }
        if (!Boolean.TRUE.equals(category.getIsActive())) {
            throw new BusinessException("A categoria " + category.getName() + " está inativa");
        }
        return category;
    }

    private static void rejectNonWritable(Map<String, Object> values, CategoryAccess access) {
        List<String> forbidden = values.entrySet().stream()
            .filter(entry -> entry.getValue() != null && !access.canWriteColumn(entry.getKey()))
            .map(Map.Entry::getKey)
            .sorted()
            .toList();
        if (!forbidden.isEmpty()) {
            throw new ForbiddenException("Sem permissão para preencher: " + String.join(", ", forbidden));
        }
    }

    @Transactional(readOnly = true)
    public List<DynamicEntityResponse> findAll(UUID categoryId) {
        if (categoryId != null) {
            CategoryAccess access = accessControl.forCategory(categoryId).requireRead();
            return dynamicEntityRepository.findByCategoryId(categoryId).stream()
                .map(entity -> DynamicEntityResponse.from(entity, access))
                .toList();
        }

        // Sem filtro: só registros das categorias que o usuário pode ler
        List<DynamicEntity> entities = accessControl.isAdmin()
            ? dynamicEntityRepository.findAll()
            : dynamicEntityRepository.findByCategoryIdIn(accessControl.readableCategoryIds());
        Map<UUID, CategoryAccess> accessByCategory = new HashMap<>();
        return entities.stream()
            .map(entity -> DynamicEntityResponse.from(entity, accessByCategory.computeIfAbsent(
                entity.getCategory().getId(), accessControl::forCategory)))
            .toList();
    }

    @Transactional(readOnly = true)
    public DynamicEntityResponse findById(UUID id) {
        DynamicEntity entity = getDynamicEntityById(id);
        CategoryAccess access = accessControl.forCategory(entity.getCategory().getId()).requireRead();
        return DynamicEntityResponse.from(entity, access);
    }

    // Filtros tipados por operador; filtrar ou ordenar por campo oculto revelaria o valor dele, então é proibido
    @Transactional(readOnly = true)
    public List<DynamicEntityResponse> search(DynamicEntitySearchRequest request) {
        categoryService.getCategoryById(request.categoryId());
        CategoryAccess access = accessControl.forCategory(request.categoryId()).requireRead();

        var compiled = DynamicEntityFilterCompiler.compile(
            request, categoryFieldService.getFields(request.categoryId()), access);

        return dynamicEntitySearchRepository.search(request.categoryId(), compiled.criteria(), compiled.sort())
            .stream().map(entity -> DynamicEntityResponse.from(entity, access)).toList();
    }

    @Transactional
    public DynamicEntityResponse create(DynamicEntityRequest request) {
        Category category = getDynamicCategory(request.categoryId());
        CategoryAccess access = accessControl.forCategory(category.getId()).requireCreate();
        rejectNonWritable(request.customFields(), access);
        Map<String, Object> customFields = categoryFieldService.validateCustomFields(category.getId(), request.customFields());

        DynamicEntity entity = dynamicEntityRepository.saveAndFlush(DynamicEntity.builder()
            .category(category)
            .customFields(customFields)
            .createdBy(accessControl.requireUser())
            .build());

        activityService.record(RecordType.DYNAMIC_ENTITY, entity.getId(), ActivityType.CREATED,
            "Cadastro criado em " + category.getName(), null);
        return DynamicEntityResponse.from(entity, access);
    }

    @Transactional
    public DynamicEntityResponse update(UUID id, DynamicEntityRequest request) {
        DynamicEntity entity = getDynamicEntityById(id);
        CategoryAccess access = accessControl.forCategory(entity.getCategory().getId()).requireUpdate();
        Map<String, Object> changes = new LinkedHashMap<>();

        // Mover para outra categoria exige permissão de criação no destino
        if (!entity.getCategory().getId().equals(request.categoryId())) {
            Category newCategory = getDynamicCategory(request.categoryId());
            access = accessControl.forCategory(newCategory.getId()).requireCreate();
            ActivityService.trackChange(changes, "categoria", entity.getCategory().getName(), newCategory.getName());
            entity.setCategory(newCategory);
        }

        Map<String, Object> oldFields = entity.getCustomFields() != null ? entity.getCustomFields() : Map.of();

        // Alterar campo sem permissão de escrita é recusado; reenviar o valor atual é aceito
        CategoryAccess writeAccess = access;
        List<String> forbidden = request.customFields().entrySet().stream()
            .filter(entry -> !writeAccess.canWriteColumn(entry.getKey()))
            .filter(entry -> entry.getValue() != null && !Objects.equals(entry.getValue(), oldFields.get(entry.getKey())))
            .map(Map.Entry::getKey)
            .sorted()
            .toList();
        if (!forbidden.isEmpty()) {
            throw new ForbiddenException("Sem permissão para alterar: " + String.join(", ", forbidden));
        }

        Map<String, Object> merged = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : request.customFields().entrySet()) {
            if (access.canWriteColumn(entry.getKey())) {
                merged.put(entry.getKey(), entry.getValue());
            }
        }
        for (Map.Entry<String, Object> entry : oldFields.entrySet()) {
            if (!access.canWriteColumn(entry.getKey())) {
                merged.put(entry.getKey(), entry.getValue());
            }
        }

        Map<String, Object> newFields = categoryFieldService.validateCustomFields(request.categoryId(), merged);
        Set<String> keys = new HashSet<>(oldFields.keySet());
        keys.addAll(newFields.keySet());
        keys.stream().sorted()
            .forEach(key -> ActivityService.trackChange(changes, key, oldFields.get(key), newFields.get(key)));

        entity.setCustomFields(newFields);

        if (!changes.isEmpty()) {
            activityService.record(RecordType.DYNAMIC_ENTITY, id, ActivityType.UPDATED, "Cadastro atualizado", changes);
        }
        return DynamicEntityResponse.from(dynamicEntityRepository.saveAndFlush(entity), access);
    }

    // O histórico permanece no ledger mesmo após a exclusão; os documentos são removidos
    @Transactional
    public void delete(UUID id) {
        DynamicEntity entity = getDynamicEntityById(id);
        accessControl.forCategory(entity.getCategory().getId()).requireDelete();
        fileService.deleteAllFor(RecordType.DYNAMIC_ENTITY, id);
        activityService.record(RecordType.DYNAMIC_ENTITY, id, ActivityType.DELETED,
            "Cadastro excluído de " + entity.getCategory().getName(), null);
        dynamicEntityRepository.delete(entity);
    }
}
