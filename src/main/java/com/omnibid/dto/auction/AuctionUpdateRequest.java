package com.omnibid.dto.auction;

import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;
import java.time.Instant;

public record AuctionUpdateRequest(
        @DecimalMin(value = "0.01", message = "Start price must be greater than 0")
        BigDecimal startPrice,

        @DecimalMin(value = "0.01", message = "Bid increment must be greater than 0")
        BigDecimal bidIncrement,

        BigDecimal reservePrice,

        Instant startTime,

        Instant endTime
) {}
