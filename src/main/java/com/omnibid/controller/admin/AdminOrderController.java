package com.omnibid.controller.admin;

import com.omnibid.common.ApiResponse;
import com.omnibid.common.PageResponse;
import com.omnibid.dto.admin.AdminCancelOrderRequest;
import com.omnibid.dto.admin.AdminOrderDetailResponse;
import com.omnibid.dto.order.OrderSummaryResponse;
import com.omnibid.enums.OrderStatus;
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

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminOrderController {

    private final AdminService adminService;

    @GetMapping
    public ApiResponse<PageResponse<OrderSummaryResponse>> getOrders(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) Long buyerId,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) Instant fromDate,
            @RequestParam(required = false) Instant toDate,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.ok(adminService.getOrders(CurrentUser.of(userDetails), status, buyerId, sellerId, fromDate, toDate, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminOrderDetailResponse> getOrderById(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(adminService.getOrderById(CurrentUser.of(userDetails), id));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<AdminOrderDetailResponse> cancelOrder(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody AdminCancelOrderRequest request
    ) {
        return ApiResponse.ok(adminService.cancelOrder(CurrentUser.of(userDetails), id, request));
    }
}
