package com.omnibid.dto.payment;

import com.omnibid.enums.ShipmentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentSummaryDto {
    private Long id;
    private String trackingCode;
    private ShipmentStatus status;
}
