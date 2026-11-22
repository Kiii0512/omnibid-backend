package com.omnibid.service;

import com.omnibid.common.PageResponse;
import com.omnibid.dto.category.AdminCategoryResponse;
import com.omnibid.dto.category.CategoryRequest;
import com.omnibid.dto.category.CategoryResponse;
import com.omnibid.security.CurrentUser;
import org.springframework.data.domain.Pageable;

public interface CategoryService {

    PageResponse<CategoryResponse> getCategories(String keyword, Pageable pageable);

    PageResponse<AdminCategoryResponse> getAdminCategories(CurrentUser admin, String keyword, Pageable pageable);

    AdminCategoryResponse createCategory(CurrentUser admin, CategoryRequest request);

    AdminCategoryResponse updateCategory(CurrentUser admin, Long id, CategoryRequest request);

    void deleteCategory(CurrentUser admin, Long id);
}
