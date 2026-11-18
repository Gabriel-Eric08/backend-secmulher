package com.secmulher.modules.core.domain.services;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.secmulher.modules.core.domain.access.AccessControlService;
import com.secmulher.modules.core.domain.dtos.CategoryDtos.CategoryRequest;
import com.secmulher.modules.core.domain.dtos.CategoryDtos.CategoryResponse;
import com.secmulher.modules.core.domain.exceptions.ConflictException;
import com.secmulher.modules.core.domain.exceptions.NotFoundException;
import com.secmulher.modules.core.infra.entities.Category;
import com.secmulher.modules.core.infra.repositories.CategoryRepository;

// Estrutura das categorias é gerida por administradores; os demais só enxergam as que podem ler
@Service
public class CategoryService {
    final CategoryRepository categoryRepository;
    final AccessControlService accessControl;
    final ActivityTypeService activityTypeService;

    public CategoryService(CategoryRepository categoryRepository, AccessControlService accessControl,
            ActivityTypeService activityTypeService) {
        this.categoryRepository = categoryRepository;
        this.accessControl = accessControl;
        this.activityTypeService = activityTypeService;
    }

    public Category getCategoryById(UUID id) {
        return categoryRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Categoria não encontrada: " + id));
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> findAll() {
        List<Category> categories = accessControl.isAdmin()
            ? categoryRepository.findAll()
            : categoryRepository.findAllById(accessControl.readableCategoryIds());
        return categories.stream().map(CategoryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse findById(UUID id) {
        Category category = getCategoryById(id);
        accessControl.forCategory(id).requireRead();
        return CategoryResponse.from(category);
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        accessControl.requireAdmin();
        if (categoryRepository.existsByName(request.name())) {
            throw new ConflictException("Já existe uma categoria com o nome: " + request.name());
        }

        Category category = Category.builder()
            .name(request.name())
            .entityType(request.entityType())
            .tableReference(request.tableReference())
            .isActive(Objects.requireNonNullElse(request.isActive(), true))
            .build();

        Category saved = categoryRepository.saveAndFlush(category);
        activityTypeService.createDefaults(saved);
        return CategoryResponse.from(saved);
    }

    @Transactional
    public CategoryResponse update(UUID id, CategoryRequest request) {
        accessControl.requireAdmin();
        Category category = getCategoryById(id);

        if (!category.getName().equals(request.name()) && categoryRepository.existsByName(request.name())) {
            throw new ConflictException("Já existe uma categoria com o nome: " + request.name());
        }

        category.setName(request.name());
        category.setEntityType(request.entityType());
        category.setTableReference(request.tableReference());
        if (request.isActive() != null) {
            category.setIsActive(request.isActive());
        }

        return CategoryResponse.from(categoryRepository.saveAndFlush(category));
    }

    @Transactional
    public void delete(UUID id) {
        accessControl.requireAdmin();
        Category category = getCategoryById(id);
        // Os tipos de registro saem junto com a categoria, mas o histórico (imutável) não pode perder o tipo
        if (activityTypeService.isUsedInCategory(id)) {
            throw new ConflictException("A categoria '" + category.getName() + "' tem registros manuais na linha do "
                + "tempo e não pode ser excluída. Desative-a com isActive: false");
        }
        categoryRepository.delete(category);
    }
}
