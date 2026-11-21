package com.omnibid.dto.order;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.omnibid.enums.OrderStatus;
import com.omnibid.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderDetailResponse {
    private Long id;
    private Long auctionId;
    private Long productId;
    private String productTitle;
    private String productImageUrl;
    private Long sellerId;
    private String sellerName;
    private Long buyerId;
    private String buyerName;
    private BigDecimal finalPrice;
    private OrderStatus status;
    private String shippingAddress;
    private String phoneNumber;
    private String trackingCode;
    private Instant paymentDeadline;
    private Instant paidAt;
    private Instant shippedAt;
    private Instant confirmDeadline;
    private Instant completedAt;
    private Instant cancelledAt;
    private String cancelReason;
    private PaymentSummaryDto latestPayment;
    private Boolean hasPendingComplaint;
    private String viewerRole;
    private List<String> allowedActions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PaymentSummaryDto {
        private Long paymentId;
        private Integer attemptNo;
        private PaymentStatus status;
        private String failureReason;
    }
}
