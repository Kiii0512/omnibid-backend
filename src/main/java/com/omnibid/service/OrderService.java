package com.omnibid.service;

import com.omnibid.common.PageResponse;
import com.omnibid.dto.order.OrderConfirmReceiptResponse;
import com.omnibid.dto.order.OrderDetailResponse;
import com.omnibid.dto.order.OrderResponse;
import com.omnibid.dto.order.OrderUserSummaryResponse;
import com.omnibid.dto.order.ShippingInfoRequest;
import com.omnibid.entity.Auction;
import com.omnibid.entity.Order;
import com.omnibid.enums.OrderStatus;
import com.omnibid.security.CurrentUser;
import org.springframework.data.domain.Pageable;

import java.time.Instant;

public interface OrderService {

    Order createFromAuction(Auction auction, Instant now);

    PageResponse<OrderResponse> getOrders(CurrentUser currentUser, String role, OrderStatus status, Pageable pageable);

    OrderDetailResponse getOrderById(CurrentUser currentUser, Long id);

    OrderDetailResponse updateShippingInfo(CurrentUser currentUser, Long id, ShippingInfoRequest request);

    OrderUserSummaryResponse getOrderSummary(CurrentUser currentUser);

    void expireOverdueOrders();

    OrderConfirmReceiptResponse confirmReceipt(CurrentUser currentUser, Long id);

    void autoCompleteOrders();
}
