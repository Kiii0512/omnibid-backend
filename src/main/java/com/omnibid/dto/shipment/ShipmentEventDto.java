package com.omnibid.dto.shipment;

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
public class ShipmentEventDto {
    private ShipmentStatus status;
    private String note;
    private String by;
    private LocalDateTime createdAt;
}
