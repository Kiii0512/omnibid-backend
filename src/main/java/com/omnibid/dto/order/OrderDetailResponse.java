package com.omnibid.dto.order;

import com.omnibid.dto.user.UserRef;
import com.omnibid.enums.OrderStatus;
import com.omnibid.enums.ProductStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDetailResponse {
    private Long id;
    private Long auctionId;
    private Long productId;
    private String title;
    private String primaryImageUrl;
    private BigDecimal finalPrice;
    private OrderStatus status;
    private UserRef buyer;
    private UserRef seller;
    private Instant paymentDeadline;
    private Instant confirmDeadline;
    private Instant createdAt;
    private String shippingAddress;
    private String phoneNumber;
    private String trackingCode;
    private Instant paidAt;
    private Instant shippedAt;
    private Instant completedAt;
    private Instant cancelledAt;
    private String cancelReason;
    private ProductStatus productStatus;
    private String viewerRole;
}
