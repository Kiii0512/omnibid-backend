package com.omnibid.service;

import com.omnibid.dto.category.CategoryRequest;
import com.omnibid.dto.category.CategoryResponse;
import com.omnibid.security.CurrentUser;

import java.util.List;

public interface CategoryService {

    List<CategoryResponse> getAllCategories();

    CategoryResponse getCategoryById(Long id);

    CategoryResponse createCategory(CurrentUser admin, CategoryRequest request);

    CategoryResponse updateCategory(CurrentUser admin, Long id, CategoryRequest request);

    void deleteCategory(CurrentUser admin, Long id);
}
