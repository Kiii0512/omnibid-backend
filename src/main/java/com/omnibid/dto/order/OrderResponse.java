package com.omnibid.dto.order;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.omnibid.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderResponse {
    private Long id;
    private Long auctionId;
    private Long productId;
    private String productTitle;
    private String productImageUrl;
    private BigDecimal finalPrice;
    private OrderStatus status;
    private String viewerRole;
    private LocalDateTime paymentDeadline;
    private LocalDateTime createdAt;
}
