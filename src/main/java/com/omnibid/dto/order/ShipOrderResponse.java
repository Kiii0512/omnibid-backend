package com.omnibid.dto.order;

import com.omnibid.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipOrderResponse {
    private Long id;
    private OrderStatus orderStatus;
    private Instant shippedAt;
    private Instant confirmDeadline;
    private String trackingCode;
}
