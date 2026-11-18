package com.secmulher.modules.core.domain.services;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.secmulher.modules.core.domain.access.AccessControlService;
import com.secmulher.modules.core.domain.access.CategoryAccess;
import com.secmulher.modules.core.domain.dtos.CategoryFieldDtos.CategoryFieldRequest;
import com.secmulher.modules.core.domain.dtos.CategoryFieldDtos.CategoryFieldResponse;
import com.secmulher.modules.core.domain.exceptions.BusinessException;
import com.secmulher.modules.core.domain.exceptions.ConflictException;
import com.secmulher.modules.core.domain.exceptions.FieldValidationException;
import com.secmulher.modules.core.domain.exceptions.NotFoundException;
import com.secmulher.modules.core.infra.entities.Category;
import com.secmulher.modules.core.infra.entities.CategoryField;
import com.secmulher.modules.core.infra.entities.enums.FieldType;
import com.secmulher.modules.core.infra.repositories.CategoryFieldRepository;
import com.secmulher.modules.core.infra.repositories.DynamicEntityRepository;

@Service
public class CategoryFieldService {
    final CategoryFieldRepository categoryFieldRepository;
    final DynamicEntityRepository dynamicEntityRepository;
    final CategoryService categoryService;
    final AccessControlService accessControl;

    public CategoryFieldService(CategoryFieldRepository categoryFieldRepository,
            DynamicEntityRepository dynamicEntityRepository, CategoryService categoryService,
            AccessControlService accessControl) {
        this.categoryFieldRepository = categoryFieldRepository;
        this.dynamicEntityRepository = dynamicEntityRepository;
        this.categoryService = categoryService;
        this.accessControl = accessControl;
    }

    private Category getDynamicCategory(UUID categoryId) {
        Category category = categoryService.getCategoryById(categoryId);
        if (!"dynamic".equals(category.getEntityType())) {
            throw new BusinessException("Campos customizáveis só podem ser definidos em categorias dinâmicas");
        }
        return category;
    }

    private CategoryField getField(UUID categoryId, UUID fieldId) {
        return categoryFieldRepository.findById(fieldId)
            .filter(field -> field.getCategory().getId().equals(categoryId))
            .orElseThrow(() -> new NotFoundException("Campo não encontrado nesta categoria: " + fieldId));
    }

    private List<String> normalizeOptions(CategoryFieldRequest request) {
        if (request.fieldType() != FieldType.LIST) {
            return null;
        }
        if (request.options() == null || request.options().isEmpty()) {
            throw new BusinessException("Campos do tipo LIST precisam de ao menos uma opção");
        }
        return request.options().stream().distinct().toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryField> getFields(UUID categoryId) {
        return categoryFieldRepository.findByCategoryIdOrderByPositionAscLabelAsc(categoryId);
    }

    // Cada usuário só vê a definição dos campos que pode ler
    @Transactional(readOnly = true)
    public List<CategoryFieldResponse> findAll(UUID categoryId) {
        categoryService.getCategoryById(categoryId);
        CategoryAccess access = accessControl.forCategory(categoryId).requireRead();
        return getFields(categoryId).stream()
            .filter(field -> access.canReadColumn(field.getKey()))
            .map(CategoryFieldResponse::from)
            .toList();
    }

    @Transactional
    public CategoryFieldResponse create(UUID categoryId, CategoryFieldRequest request) {
        accessControl.requireAdmin();
        Category category = getDynamicCategory(categoryId);
        if (categoryFieldRepository.existsByCategoryIdAndKey(categoryId, request.key())) {
            throw new ConflictException("Já existe um campo com a chave '" + request.key() + "' nesta categoria");
        }

        CategoryField field = CategoryField.builder()
            .category(category)
            .key(request.key())
            .label(request.label())
            .fieldType(request.fieldType())
            .isRequired(Objects.requireNonNullElse(request.isRequired(), false))
            .options(normalizeOptions(request))
            .position(Objects.requireNonNullElse(request.position(), 0))
            .build();

        return CategoryFieldResponse.from(categoryFieldRepository.saveAndFlush(field));
    }

    @Transactional
    public CategoryFieldResponse update(UUID categoryId, UUID fieldId, CategoryFieldRequest request) {
        accessControl.requireAdmin();
        CategoryField field = getField(categoryId, fieldId);

        if (!field.getKey().equals(request.key())) {
            throw new BusinessException("A chave de um campo não pode ser alterada; crie um novo campo");
        }

        // Os dados gravados precisam continuar válidos para o tipo, senão filtros e validação quebram
        UUID usedCategory = field.getCategory().getId();
        if (field.getFieldType() != request.fieldType()
                && dynamicEntityRepository.existsCustomFieldKey(usedCategory, field.getKey())) {
            throw new ConflictException("O campo '" + field.getKey() + "' já tem dados; o tipo não pode ser alterado. "
                + "Crie um novo campo ou remova este com purgeData=true");
        }
        List<String> options = normalizeOptions(request);
        if (request.fieldType() == FieldType.LIST && field.getOptions() != null) {
            List<String> removed = field.getOptions().stream().filter(option -> !options.contains(option)).toList();
            List<String> inUse = removed.isEmpty() ? List.of()
                : dynamicEntityRepository.findUsedValues(usedCategory, field.getKey(), removed);
            if (!inUse.isEmpty()) {
                throw new ConflictException("Opções ainda usadas em registros não podem ser removidas: "
                    + String.join(", ", inUse.stream().sorted().toList()));
            }
        }

        field.setLabel(request.label());
        field.setFieldType(request.fieldType());
        field.setOptions(options);
        if (request.isRequired() != null) {
            field.setIsRequired(request.isRequired());
        }
        if (request.position() != null) {
            field.setPosition(request.position());
        }

        return CategoryFieldResponse.from(categoryFieldRepository.saveAndFlush(field));
    }

    // Sem purgeData, recusa remover um campo que ainda tem dados em algum registro
    @Transactional
    public void delete(UUID categoryId, UUID fieldId, boolean purgeData) {
        accessControl.requireAdmin();
        CategoryField field = getField(categoryId, fieldId);

        if (purgeData) {
            dynamicEntityRepository.removeCustomFieldKey(categoryId, field.getKey());
        } else if (dynamicEntityRepository.existsCustomFieldKey(categoryId, field.getKey())) {
            throw new ConflictException("Existem registros com dados no campo '" + field.getKey()
                + "'. Use purgeData=true para remover o campo e apagar esses dados");
        }

        categoryFieldRepository.delete(field);
    }

    // Valida os valores contra o formulário da categoria e devolve o mapa sem valores nulos
    @Transactional(readOnly = true)
    public Map<String, Object> validateCustomFields(UUID categoryId, Map<String, Object> values) {
        List<CategoryField> fields = getFields(categoryId);
        Map<String, String> errors = new LinkedHashMap<>();
        Map<String, Object> cleaned = new LinkedHashMap<>();

        values.keySet().stream()
            .filter(key -> fields.stream().noneMatch(field -> field.getKey().equals(key)))
            .forEach(key -> errors.put(key, "campo não existe nesta categoria"));

        for (CategoryField field : fields) {
            Object value = values.get(field.getKey());
            boolean empty = value == null || (value instanceof String text && text.isBlank());

            if (empty) {
                if (Boolean.TRUE.equals(field.getIsRequired())) {
                    errors.put(field.getKey(), "campo obrigatório");
                }
                continue;
            }

            String error = validateValue(field, value);
            if (error != null) {
                errors.put(field.getKey(), error);
            } else {
                cleaned.put(field.getKey(), value);
            }
        }

        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
        return cleaned;
    }

    private String validateValue(CategoryField field, Object value) {
        return switch (field.getFieldType()) {
            case TEXT -> value instanceof String ? null : "deve ser um texto";
            case NUMBER -> value instanceof Number ? null : "deve ser um número";
            case BOOLEAN -> value instanceof Boolean ? null : "deve ser verdadeiro ou falso";
            case DATE -> isIsoDate(value) ? null : "deve ser uma data no formato AAAA-MM-DD";
            case LIST -> field.getOptions() != null && field.getOptions().contains(value)
                ? null
                : "deve ser uma das opções: " + field.getOptions();
        };
    }

    private boolean isIsoDate(Object value) {
        if (!(value instanceof String text)) {
            return false;
        }
        try {
            LocalDate.parse(text);
            return true;
        } catch (DateTimeParseException ex) {
            return false;
        }
    }
}
