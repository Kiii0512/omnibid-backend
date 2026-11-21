package com.omnibid.dto.admin;

import com.omnibid.dto.order.OrderDetailResponse;
import com.omnibid.dto.payment.PaymentHistoryResponse;

import java.util.List;

public record AdminOrderDetailResponse(
        OrderDetailResponse order,
        List<PaymentHistoryResponse> payments
) {}
