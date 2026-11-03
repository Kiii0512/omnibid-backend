package com.omnibid.controller;

import com.omnibid.common.ApiResponse;
import com.omnibid.common.PageResponse;
import com.omnibid.dto.admin.*;
import com.omnibid.dto.auction.AuctionResponse;
import com.omnibid.dto.category.CategoryRequest;
import com.omnibid.dto.category.CategoryResponse;
import com.omnibid.dto.order.OrderResponse;
import com.omnibid.enums.OrderStatus;
import com.omnibid.enums.ProductStatus;
import com.omnibid.enums.UserStatus;
import com.omnibid.security.CurrentUser;
import com.omnibid.security.CustomUserDetails;
import com.omnibid.service.AdminService;
import com.omnibid.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final CategoryService categoryService;

    // =========================================================================
    // USERS (ADM-01, 02, 06)
    // =========================================================================

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<PageResponse<AdminUserResponse>>> getUsers(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        CurrentUser currentUser = CurrentUser.of(userDetails);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        PageResponse<AdminUserResponse> response = adminService.getUsers(currentUser, status, q, pageable);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/users/{id}")
    public ResponseEntity<ApiResponse<UserStatusUpdateResponse>> updateUserStatus(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody UserStatusUpdateRequest request) {

        CurrentUser currentUser = CurrentUser.of(userDetails);
        UserStatusUpdateResponse response = adminService.updateUserStatus(currentUser, id, request);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // =========================================================================
    // PRODUCTS (ADM-07, 08)
    // =========================================================================

    @GetMapping("/products")
    public ResponseEntity<ApiResponse<PageResponse<AdminProductResponse>>> getProducts(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        CurrentUser currentUser = CurrentUser.of(userDetails);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        PageResponse<AdminProductResponse> response = adminService.getProducts(currentUser, status, sellerId, categoryId, pageable);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/products/{id}")
    public ResponseEntity<ApiResponse<ProductHideResponse>> hideProduct(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody ProductHideRequest request) {

        CurrentUser currentUser = CurrentUser.of(userDetails);
        ProductHideResponse response = adminService.hideProduct(currentUser, id, request);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // =========================================================================
    // AUCTIONS (ADM-09)
    // =========================================================================

    @PostMapping("/auctions/{id}/cancel")
    public ResponseEntity<ApiResponse<AuctionResponse>> cancelAuction(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody AdminCancelAuctionRequest request) {

        CurrentUser currentUser = CurrentUser.of(userDetails);
        AuctionResponse response = adminService.cancelAuction(currentUser, id, request);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // =========================================================================
    // CATEGORIES (ADM-10)
    // =========================================================================

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getCategories() {
        List<CategoryResponse> response = categoryService.getAllCategories();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/categories")
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CategoryRequest request) {

        CurrentUser currentUser = CurrentUser.of(userDetails);
        CategoryResponse response = categoryService.createCategory(currentUser, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PutMapping("/categories/{id}")
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategory(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody CategoryRequest request) {

        CurrentUser currentUser = CurrentUser.of(userDetails);
        CategoryResponse response = categoryService.updateCategory(currentUser, id, request);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id) {

        CurrentUser currentUser = CurrentUser.of(userDetails);
        categoryService.deleteCategory(currentUser, id);

        return ResponseEntity.ok(ApiResponse.success("Category deleted", null));
    }

    // =========================================================================
    // ORDERS (ADM-11, 12)
    // =========================================================================

    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<PageResponse<AdminOrderSummaryResponse>>> getOrders(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) Long buyerId,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        CurrentUser currentUser = CurrentUser.of(userDetails);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        PageResponse<AdminOrderSummaryResponse> response = adminService.getOrders(currentUser, status, buyerId, sellerId, from, to, pageable);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/orders/{id}/cancel")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody AdminCancelOrderRequest request) {

        CurrentUser currentUser = CurrentUser.of(userDetails);
        OrderResponse response = adminService.cancelOrder(currentUser, id, request);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // =========================================================================
    // STATISTICS & AUDIT LOGS (ADM-14, 15)
    // =========================================================================

    @GetMapping("/statistics")
    public ResponseEntity<ApiResponse<AdminStatisticsResponse>> getStatistics(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        CurrentUser currentUser = CurrentUser.of(userDetails);
        AdminStatisticsResponse response = adminService.getStatistics(currentUser, from, to);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<ApiResponse<PageResponse<AuditLogResponse>>> getAuditLogs(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) Long adminId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) Long targetId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        CurrentUser currentUser = CurrentUser.of(userDetails);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        PageResponse<AuditLogResponse> response = adminService.getAuditLogs(currentUser, adminId, action, targetType, targetId, from, to, pageable);

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
