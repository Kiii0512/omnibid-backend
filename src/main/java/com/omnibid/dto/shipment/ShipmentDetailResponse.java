package com.omnibid.dto.shipment;

import com.omnibid.enums.ShipmentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentDetailResponse {
    private Long id;
    private Long orderId;
    private String trackingCode;
    private String carrierName;
    private ShipmentStatus status;
    private List<ShipmentEventDto> events;
}
