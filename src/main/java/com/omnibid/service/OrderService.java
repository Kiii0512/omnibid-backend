package com.omnibid.service;

import com.omnibid.common.PageResponse;
import com.omnibid.dto.order.OrderDetailResponse;
import com.omnibid.dto.order.OrderResponse;
import com.omnibid.dto.order.ShippingInfoRequest;
import com.omnibid.entity.Auction;
import com.omnibid.entity.Order;
import com.omnibid.enums.OrderStatus;
import com.omnibid.security.CurrentUser;
import org.springframework.data.domain.Pageable;

public interface OrderService {

    Order createFromAuction(Auction auction);

    PageResponse<OrderResponse> getOrders(CurrentUser currentUser, String role, OrderStatus status, Pageable pageable);

    OrderDetailResponse getOrderById(CurrentUser currentUser, Long id);

    OrderDetailResponse updateShippingInfo(CurrentUser currentUser, Long id, ShippingInfoRequest request);

    void sendPaymentReminders();

    void expireOverdueOrders();

    com.omnibid.dto.order.OrderConfirmReceiptResponse confirmReceipt(CurrentUser currentUser, Long id);

    void autoCompleteOrders();
}
