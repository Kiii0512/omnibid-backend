package com.omnibid.dto.order;

import jakarta.validation.constraints.Size;

public record ShipOrderRequest(
        @Size(max = 50, message = "trackingCode tối đa 50 ký tự")
        String trackingCode
) {}
