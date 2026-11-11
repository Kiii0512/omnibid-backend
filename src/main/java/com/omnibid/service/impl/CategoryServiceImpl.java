package com.omnibid.service.impl;

import com.omnibid.common.AppException;
import com.omnibid.dto.category.CategoryResponse;
import com.omnibid.entity.Category;
import com.omnibid.repository.CategoryRepository;
import com.omnibid.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllCategories() {
        List<Category> categories = categoryRepository.findAll();

        List<Object[]> counts = categoryRepository.countActiveAuctionsGroupedByCategory();
        Map<Long, Long> countMap = counts.stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]
                ));

        return categories.stream()
                .map(cat -> CategoryResponse.builder()
                        .id(cat.getId())
                        .name(cat.getName())
                        .slug(cat.getSlug())
                        .iconUrl(cat.getIconUrl())
                        .activeAuctionCount(countMap.getOrDefault(cat.getId(), 0L))
                        .createdAt(cat.getCreatedAt())
                        .build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(Long id) {
        Category cat = categoryRepository.findById(id)
                .orElseThrow(() -> new AppException("Không tìm thấy danh mục với id: " + id));

        long count = categoryRepository.countActiveAuctionsByCategoryId(id);

        return CategoryResponse.builder()
                .id(cat.getId())
                .name(cat.getName())
                .slug(cat.getSlug())
                .iconUrl(cat.getIconUrl())
                .activeAuctionCount(count)
                .createdAt(cat.getCreatedAt())
                .build();
    }
}