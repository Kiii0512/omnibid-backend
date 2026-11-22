package com.omnibid.dto.order;

import com.omnibid.dto.user.UserRef;
import com.omnibid.enums.OrderStatus;
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
public class OrderSummaryResponse {
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
}
