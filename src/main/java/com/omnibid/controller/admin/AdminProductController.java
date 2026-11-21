package com.omnibid.controller.admin;

import com.omnibid.common.ApiResponse;
import com.omnibid.common.PageResponse;
import com.omnibid.common.SortWhitelist;
import com.omnibid.dto.admin.AdminProductResponse;
import com.omnibid.dto.admin.ProductHideRequest;
import com.omnibid.dto.admin.ProductHideResponse;
import com.omnibid.dto.product.ProductResponse;
import com.omnibid.enums.ProductStatus;
import com.omnibid.security.CurrentUser;
import com.omnibid.security.CustomUserDetails;
import com.omnibid.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/admin/products")
@RequiredArgsConstructor
public class AdminProductController {

    private final AdminService adminService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AdminProductResponse>>> getProducts(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) ProductStatus status,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        SortWhitelist.validate(pageable, Set.of("createdAt", "id", "title", "status"));
        CurrentUser admin = CurrentUser.of(userDetails);
        PageResponse<AdminProductResponse> response = adminService.getProducts(admin, keyword, categoryId, status, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductById(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id) {

        CurrentUser admin = CurrentUser.of(userDetails);
        ProductResponse response = adminService.getProductById(admin, id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/hide")
    public ResponseEntity<ApiResponse<ProductHideResponse>> hideProduct(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @RequestBody(required = false) ProductHideRequest request) {

        CurrentUser admin = CurrentUser.of(userDetails);
        String reason = request != null ? request.reason() : null;
        ProductHideResponse response = adminService.hideProduct(admin, id, reason);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
