package com.omnibid.service.impl;

import com.omnibid.common.BusinessException;
import com.omnibid.common.ErrorCode;
import com.omnibid.common.PageResponse;
import com.omnibid.dto.category.CategoryRequest;
import com.omnibid.dto.category.CategoryResponse;
import com.omnibid.entity.Category;
import com.omnibid.repository.AuctionRepository;
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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final AuctionRepository auctionRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CategoryResponse> getCategories(String q, Pageable pageable) {
        Page<Category> page = categoryRepository.searchCategories(q, pageable);
        Map<Long, Long> counts = getActiveAuctionCounts();
        return PageResponse.from(page, cat -> new CategoryResponse(
                cat.getId(),
                cat.getName(),
                cat.getSlug(),
                cat.getIcon(),
                cat.getCreatedAt(),
                counts.getOrDefault(cat.getId(), 0L)
        ));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllCategories() {
        Map<Long, Long> counts = getActiveAuctionCounts();
        return categoryRepository.findAll().stream()
                .map(cat -> new CategoryResponse(
                        cat.getId(),
                        cat.getName(),
                        cat.getSlug(),
                        cat.getIcon(),
                        cat.getCreatedAt(),
                        counts.getOrDefault(cat.getId(), 0L)
                ))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Category not found"));
        long activeCount = auctionRepository.countActiveAuctionsByCategoryId(id);
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getSlug(),
                category.getIcon(),
                category.getCreatedAt(),
                activeCount
        );
    }

    @Override
    @Transactional
    public CategoryResponse createCategory(CurrentUser admin, CategoryRequest request) {
        requireAdmin(admin);

        String slug = request.slug() != null && !request.slug().isBlank() ? request.slug() : slugify(request.name());
        if (categoryRepository.existsBySlug(slug)) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Slug danh mục đã tồn tại",
                    Map.of("fields", Map.of("slug", "Slug đã tồn tại"))
            );
        }

        Category category = Category.builder()
                .name(request.name().trim())
                .slug(slug)
                .icon(request.icon())
                .build();
        category = categoryRepository.save(category);

        log.info("Admin {} created category: id={}, name={}", admin.id(), category.getId(), category.getName());
        return mapToResponse(category);
    }

    @Override
    @Transactional
    public CategoryResponse updateCategory(CurrentUser admin, Long id, CategoryRequest request) {
        requireAdmin(admin);

        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Category not found"));

        String slug = request.slug() != null && !request.slug().isBlank() ? request.slug() : category.getSlug();
        if (categoryRepository.existsBySlugAndIdNot(slug, id)) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Slug danh mục đã tồn tại",
                    Map.of("fields", Map.of("slug", "Slug đã tồn tại"))
            );
        }

        category.setName(request.name().trim());
        category.setSlug(slug);
        category.setIcon(request.icon());

        category = categoryRepository.save(category);
        log.info("Admin {} updated category: id={}", admin.id(), id);
        return mapToResponse(category);
    }

    @Override
    @Transactional
    public void deleteCategory(CurrentUser admin, Long id) {
        requireAdmin(admin);

        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Category not found"));

        if (productRepository.existsByCategoryId(id)) {
            throw new BusinessException(ErrorCode.CATEGORY_IN_USE, "Không thể xóa danh mục đang có sản phẩm");
        }

        categoryRepository.delete(category);
        log.info("Admin {} deleted category: id={}", admin.id(), id);
    }

    private Map<Long, Long> getActiveAuctionCounts() {
        return auctionRepository.countActiveAuctionsGroupedByCategory().stream()
                .filter(row -> row != null && row.length >= 2 && row[0] != null)
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> ((Number) row[1]).longValue(),
                        (a, b) -> a
                ));
    }

    private void requireAdmin(CurrentUser admin) {
        if (!admin.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Yêu cầu quyền quản trị viên");
        }
    }

    private CategoryResponse mapToResponse(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getSlug(),
                category.getIcon(),
                category.getCreatedAt(),
                0L
        );
    }

    private String slugify(String input) {
        if (input == null) return "";
        String preprocessed = input.replace('đ', 'd').replace('Đ', 'D');
        String normalized = Normalizer.normalize(preprocessed, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return normalized.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("[\\s]+", "-")
                .replaceAll("^-+|-+$", "");
    }
}
