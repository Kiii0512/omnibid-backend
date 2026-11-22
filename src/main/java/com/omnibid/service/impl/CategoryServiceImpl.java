package com.omnibid.service.impl;

import com.omnibid.common.BusinessException;
import com.omnibid.common.ErrorCode;
import com.omnibid.common.PageResponse;
import com.omnibid.common.SortWhitelist;
import com.omnibid.dto.category.AdminCategoryResponse;
import com.omnibid.dto.category.CategoryRequest;
import com.omnibid.dto.category.CategoryResponse;
import com.omnibid.entity.Category;
import com.omnibid.repository.CategoryRepository;
import com.omnibid.repository.ProductRepository;
import com.omnibid.security.CurrentUser;
import com.omnibid.service.CategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private static final Set<String> CATEGORY_SORT_FIELDS = Set.of("id", "name", "slug", "createdAt");

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CategoryResponse> getCategories(String keyword, Pageable pageable) {
        SortWhitelist.validate(pageable, CATEGORY_SORT_FIELDS);
        String normalized = normalizeKeyword(keyword);
        Page<Category> page = categoryRepository.searchByKeyword(normalized, pageable);
        return PageResponse.from(page, this::toCategoryResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminCategoryResponse> getAdminCategories(CurrentUser admin, String keyword, Pageable pageable) {
        requireAdmin(admin);
        SortWhitelist.validate(pageable, CATEGORY_SORT_FIELDS);
        String normalized = normalizeKeyword(keyword);
        Page<Category> page = categoryRepository.searchByKeyword(normalized, pageable);

        List<Category> categories = page.getContent();
        if (categories.isEmpty()) {
            return PageResponse.from(page, c -> null);
        }

        List<Long> ids = categories.stream().map(Category::getId).toList();
        Map<Long, Long> productCounts = new HashMap<>();
        for (Object[] row : productRepository.countGroupedByCategoryIdIn(ids)) {
            if (row != null && row.length >= 2 && row[0] != null) {
                productCounts.put((Long) row[0], ((Number) row[1]).longValue());
            }
        }

        return PageResponse.from(page, c -> toAdminCategoryResponse(c, productCounts.getOrDefault(c.getId(), 0L)));
    }

    @Override
    @Transactional
    public AdminCategoryResponse createCategory(CurrentUser admin, CategoryRequest request) {
        requireAdmin(admin);

        String slug = resolveSlug(request);
        if (categoryRepository.existsBySlug(slug)) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Slug danh mục '" + slug + "' đã tồn tại",
                    Map.of("fields", Map.of("slug", "Slug đã tồn tại"))
            );
        }

        Category category = Category.builder()
                .name(request.name().trim())
                .slug(slug)
                .icon(request.icon())
                .build();
        category = categoryRepository.save(category);

        log.info("Admin {} action=CATEGORY_CREATE target=CATEGORY:{} name={} slug={}",
                admin.id(), category.getId(), category.getName(), category.getSlug());

        return toAdminCategoryResponse(category, 0L);
    }

    @Override
    @Transactional
    public AdminCategoryResponse updateCategory(CurrentUser admin, Long id, CategoryRequest request) {
        requireAdmin(admin);

        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy danh mục"));

        String slug = resolveSlug(request);
        if (categoryRepository.existsBySlugAndIdNot(slug, id)) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Slug danh mục '" + slug + "' đã tồn tại",
                    Map.of("fields", Map.of("slug", "Slug đã tồn tại"))
            );
        }

        category.setName(request.name().trim());
        category.setSlug(slug);
        category.setIcon(request.icon());
        category = categoryRepository.save(category);

        long productCount = productRepository.countByCategoryId(id);

        log.info("Admin {} action=CATEGORY_UPDATE target=CATEGORY:{} name={} slug={}",
                admin.id(), category.getId(), category.getName(), category.getSlug());

        return toAdminCategoryResponse(category, productCount);
    }

    @Override
    @Transactional
    public void deleteCategory(CurrentUser admin, Long id) {
        requireAdmin(admin);

        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy danh mục"));

        if (productRepository.existsByCategoryId(id)) {
            throw new BusinessException(ErrorCode.CATEGORY_IN_USE, "Không thể xóa danh mục đang có sản phẩm");
        }

        categoryRepository.delete(category);

        log.info("Admin {} action=CATEGORY_DELETE target=CATEGORY:{} name={}", admin.id(), id, category.getName());
    }

    public static String slugify(String input) {
        if (input == null) {
            return "";
        }
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        String withoutDiacritics = normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        withoutDiacritics = withoutDiacritics.replace("đ", "d").replace("Đ", "d");
        return withoutDiacritics.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .trim()
                .replaceAll("[\\s-]+", "-");
    }

    private String resolveSlug(CategoryRequest request) {
        String slug = (request.slug() != null && !request.slug().isBlank())
                ? request.slug().trim().toLowerCase()
                : slugify(request.name());
        if (slug.isBlank()) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Slug danh mục không hợp lệ",
                    Map.of("fields", Map.of("slug", "Slug không hợp lệ"))
            );
        }
        return slug;
    }

    private String normalizeKeyword(String keyword) {
        return (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
    }

    private void requireAdmin(CurrentUser admin) {
        if (admin == null || !admin.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Yêu cầu quyền quản trị viên");
        }
    }

    private CategoryResponse toCategoryResponse(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getSlug(),
                category.getIcon()
        );
    }

    private AdminCategoryResponse toAdminCategoryResponse(Category category, long productCount) {
        return new AdminCategoryResponse(
                category.getId(),
                category.getName(),
                category.getSlug(),
                category.getIcon(),
                productCount
        );
    }
}
