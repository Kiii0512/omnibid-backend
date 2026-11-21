package com.omnibid.controller.admin;

import com.omnibid.common.ApiResponse;
import com.omnibid.common.PageResponse;
import com.omnibid.common.SortWhitelist;
import com.omnibid.dto.admin.AdminCancelOrderRequest;
import com.omnibid.dto.admin.AdminOrderDetailResponse;
import com.omnibid.dto.admin.AdminOrderSummaryResponse;
import com.omnibid.dto.order.OrderResponse;
import com.omnibid.enums.OrderStatus;
import com.omnibid.security.CurrentUser;
import com.omnibid.security.CustomUserDetails;
import com.omnibid.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private final AdminService adminService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AdminOrderSummaryResponse>>> getOrders(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) Long buyerId,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) Instant fromDate,
            @RequestParam(required = false) Instant toDate,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        SortWhitelist.validate(pageable, Set.of("createdAt", "id", "finalPrice", "status"));
        CurrentUser admin = CurrentUser.of(userDetails);
        PageResponse<AdminOrderSummaryResponse> response = adminService.getOrders(admin, status, buyerId, sellerId, fromDate, toDate, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminOrderDetailResponse>> getOrderById(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id) {

        CurrentUser admin = CurrentUser.of(userDetails);
        AdminOrderDetailResponse response = adminService.getOrderById(admin, id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody AdminCancelOrderRequest request) {

        CurrentUser admin = CurrentUser.of(userDetails);
        OrderResponse response = adminService.cancelOrder(admin, id, request.reason());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
