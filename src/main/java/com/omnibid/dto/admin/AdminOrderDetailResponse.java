package com.omnibid.dto.admin;

import com.omnibid.dto.payment.PaymentHistoryResponse;
import com.omnibid.dto.user.UserSummary;
import com.omnibid.enums.OrderStatus;
import com.omnibid.enums.ProductStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AdminOrderDetailResponse(
        Long id,
        Long auctionId,
        Long productId,
        String title,
        String primaryImageUrl,
        BigDecimal finalPrice,
        OrderStatus status,
        UserSummary buyer,
        UserSummary seller,
        Instant paymentDeadline,
        Instant confirmDeadline,
        Instant createdAt,
        String shippingAddress,
        String phoneNumber,
        String trackingCode,
        Instant paidAt,
        Instant shippedAt,
        Instant completedAt,
        Instant cancelledAt,
        String cancelReason,
        ProductStatus productStatus,
        List<PaymentHistoryResponse> payments
) {}
