package com.omnibid.controller;

import com.omnibid.common.ApiResponse;
import com.omnibid.common.PageResponse;
import com.omnibid.dto.category.CategoryResponse;
import com.omnibid.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public ApiResponse<PageResponse<CategoryResponse>> getCategories(
            @RequestParam(required = false) String q,
            @PageableDefault(size = 10) Pageable pageable
    ) {
        PageResponse<CategoryResponse> response = categoryService.getCategories(q, pageable);
        return ApiResponse.success(response);
    }
}
