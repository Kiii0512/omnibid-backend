package com.omnibid.service.impl;

import com.omnibid.common.AppException;
import com.omnibid.dto.category.CategoryRequest;
import com.omnibid.dto.category.CategoryResponse;
import com.omnibid.entity.Category;
import com.omnibid.repository.CategoryRepository;
import com.omnibid.repository.ProductRepository;
import com.omnibid.security.CurrentUser;
import com.omnibid.service.AuditService;
import com.omnibid.service.CategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final AuditService auditService;

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAll().stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new AppException("NOT_FOUND", "Category not found", HttpStatus.NOT_FOUND));
        return mapToResponse(category);
    }

    @Override
    @Transactional
    public CategoryResponse createCategory(CurrentUser admin, CategoryRequest request) {
        requireAdmin(admin);

        String slug = slugify(request.name());
        if (categoryRepository.existsBySlug(slug)) {
            throw new AppException("VALIDATION_ERROR", "Category with slug '" + slug + "' already exists", HttpStatus.CONFLICT);
        }

        Category category = Category.builder()
                .name(request.name().trim())
                .slug(slug)
                .iconUrl(request.iconUrl())
                .build();
        category = categoryRepository.save(category);

        auditService.log(admin.id(), "CATEGORY_CREATE", "CATEGORY", category.getId(), "name=" + category.getName());

        return mapToResponse(category);
    }

    @Override
    @Transactional
    public CategoryResponse updateCategory(CurrentUser admin, Long id, CategoryRequest request) {
        requireAdmin(admin);

        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new AppException("NOT_FOUND", "Category not found", HttpStatus.NOT_FOUND));

        String slug = slugify(request.name());
        if (categoryRepository.existsBySlugAndIdNot(slug, id)) {
            throw new AppException("VALIDATION_ERROR", "Category with slug '" + slug + "' already exists", HttpStatus.CONFLICT);
        }

        category.setName(request.name().trim());
        category.setSlug(slug);
        category.setIconUrl(request.iconUrl());
        category = categoryRepository.save(category);

        auditService.log(admin.id(), "CATEGORY_UPDATE", "CATEGORY", category.getId(), "name=" + category.getName());

        return mapToResponse(category);
    }

    @Override
    @Transactional
    public void deleteCategory(CurrentUser admin, Long id) {
        requireAdmin(admin);

        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new AppException("NOT_FOUND", "Category not found", HttpStatus.NOT_FOUND));

        if (productRepository.existsByCategoryId(id)) {
            throw new AppException("CATEGORY_IN_USE", "Danh mục còn sản phẩm", HttpStatus.CONFLICT);
        }

        categoryRepository.delete(category);

        auditService.log(admin.id(), "CATEGORY_DELETE", "CATEGORY", id, "name=" + category.getName());
    }

    public static String slugify(String input) {
        if (input == null) return "";
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        String withoutDiacritics = normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        withoutDiacritics = withoutDiacritics.replace("đ", "d").replace("Đ", "d");
        return withoutDiacritics.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .trim()
                .replaceAll("\\s+", "-");
    }

    private void requireAdmin(CurrentUser admin) {
        if (!admin.isAdmin()) {
            throw new AppException("FORBIDDEN", "Admin required", HttpStatus.FORBIDDEN);
        }
    }

    private CategoryResponse mapToResponse(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getSlug(),
                category.getIconUrl(),
                category.getCreatedAt()
        );
    }
}
