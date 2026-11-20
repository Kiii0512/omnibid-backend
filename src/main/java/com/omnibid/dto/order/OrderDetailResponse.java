package com.omnibid.dto.order;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.omnibid.enums.OrderStatus;
import com.omnibid.enums.PaymentStatus;
import com.omnibid.enums.ShipmentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
    private LocalDateTime paymentDeadline;
    private LocalDateTime paidAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime confirmDeadline;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;
    private String cancelReason;
    private PaymentSummaryDto latestPayment;
    private ShipmentSummaryDto shipment;
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

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ShipmentSummaryDto {
        private Long id;
        private String trackingCode;
        private ShipmentStatus status;
    }
}
