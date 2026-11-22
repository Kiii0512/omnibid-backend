package com.omnibid.dto.order;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipOrderRequest {

    @Size(max = 50, message = "Mã vận đơn không được vượt quá 50 ký tự")
    private String trackingCode;
}
