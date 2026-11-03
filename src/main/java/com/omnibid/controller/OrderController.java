package com.omnibid.controller;

import com.omnibid.common.ApiResponse;
import com.omnibid.common.PageResponse;
import com.omnibid.dto.order.OrderDetailResponse;
import com.omnibid.dto.order.OrderResponse;
import com.omnibid.dto.order.ShippingInfoRequest;
import com.omnibid.enums.OrderStatus;
import com.omnibid.security.CurrentUser;
import com.omnibid.security.CustomUserDetails;
import com.omnibid.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        CurrentUser currentUser = CurrentUser.of(userDetails);
        PageResponse<OrderResponse> result = orderService.getOrders(
                currentUser,
                role,
                status,
                PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"))
        );
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

    @PatchMapping("/{id}/confirm-receipt")
    public ApiResponse<com.omnibid.dto.order.OrderConfirmReceiptResponse> confirmReceipt(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id
    ) {
        CurrentUser currentUser = CurrentUser.of(userDetails);
        com.omnibid.dto.order.OrderConfirmReceiptResponse result = orderService.confirmReceipt(currentUser, id);
        return ApiResponse.success("Order completed", result);
    }
}
