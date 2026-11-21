package com.omnibid.controller;

import com.omnibid.common.ApiResponse;
import com.omnibid.common.PageResponse;
import com.omnibid.common.SortWhitelist;
import com.omnibid.dto.order.*;
import com.omnibid.enums.OrderStatus;
import com.omnibid.security.CurrentUser;
import com.omnibid.security.CustomUserDetails;
import com.omnibid.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping
    public ApiResponse<PageResponse<OrderResponse>> getOrders(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) OrderStatus status,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        SortWhitelist.validate(pageable, Set.of("createdAt", "finalPrice", "status"));
        CurrentUser currentUser = CurrentUser.of(userDetails);
        PageResponse<OrderResponse> result = orderService.getOrders(currentUser, role, status, pageable);
        return ApiResponse.success(result);
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderDetailResponse> getOrderById(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id
    ) {
        CurrentUser currentUser = CurrentUser.of(userDetails);
        OrderDetailResponse result = orderService.getOrderById(currentUser, id);
        return ApiResponse.success(result);
    }

    @PatchMapping("/{id}/shipping-info")
    public ApiResponse<OrderDetailResponse> updateShippingInfo(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody ShippingInfoRequest request
    ) {
        CurrentUser currentUser = CurrentUser.of(userDetails);
        OrderDetailResponse result = orderService.updateShippingInfo(currentUser, id, request);
        return ApiResponse.success(result);
    }

    @PostMapping("/{id}/ship")
    public ApiResponse<OrderShipResponse> shipOrder(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) ShipOrderRequest request
    ) {
        CurrentUser currentUser = CurrentUser.of(userDetails);
        OrderShipResponse result = orderService.shipOrder(currentUser, id, request);
        return ApiResponse.success(result);
    }

    @PostMapping("/{id}/confirm-receipt")
    public ApiResponse<OrderConfirmReceiptResponse> confirmReceipt(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id
    ) {
        CurrentUser currentUser = CurrentUser.of(userDetails);
        OrderConfirmReceiptResponse result = orderService.confirmReceipt(currentUser, id);
        return ApiResponse.success(result);
    }
}
