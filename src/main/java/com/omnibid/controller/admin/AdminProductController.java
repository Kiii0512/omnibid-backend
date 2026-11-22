package com.omnibid.controller.admin;

import com.omnibid.common.ApiResponse;
import com.omnibid.common.PageResponse;
import com.omnibid.dto.admin.AdminProductDetailResponse;
import com.omnibid.dto.admin.AdminProductItemResponse;
import com.omnibid.dto.admin.AdminReasonRequest;
import com.omnibid.dto.admin.ProductHideResponse;
import com.omnibid.enums.ProductStatus;
import com.omnibid.security.CurrentUser;
import com.omnibid.security.CustomUserDetails;
import com.omnibid.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/products")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminProductController {

    private final AdminService adminService;

    @GetMapping
    public ApiResponse<PageResponse<AdminProductItemResponse>> getProducts(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) ProductStatus status,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.ok(adminService.getProducts(CurrentUser.of(userDetails), keyword, categoryId, status, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminProductDetailResponse> getProductById(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(adminService.getProductById(CurrentUser.of(userDetails), id));
    }

    @PostMapping("/{id}/hide")
    public ApiResponse<ProductHideResponse> hideProduct(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) AdminReasonRequest request
    ) {
        String reason = request != null ? request.reason() : null;
        return ApiResponse.ok(adminService.hideProduct(CurrentUser.of(userDetails), id, reason));
    }
}
