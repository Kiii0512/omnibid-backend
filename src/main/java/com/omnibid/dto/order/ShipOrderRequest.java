package com.omnibid.dto.order;

import jakarta.validation.constraints.Size;

public record ShipOrderRequest(
        @Size(max = 100, message = "trackingCode tối đa 100 ký tự")
        String trackingCode
) {}
