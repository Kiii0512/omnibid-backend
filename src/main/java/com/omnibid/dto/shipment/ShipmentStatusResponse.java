package com.omnibid.dto.shipment;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.omnibid.enums.OrderStatus;
import com.omnibid.enums.ShipmentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ShipmentStatusResponse {
    private Long id;
    private ShipmentStatus status;
    private OrderStatus orderStatus;
    private LocalDateTime deliveredAt;
    private LocalDateTime confirmDeadline;
}
