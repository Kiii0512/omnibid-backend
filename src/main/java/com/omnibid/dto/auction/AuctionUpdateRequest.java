package com.omnibid.dto.auction;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;

public record AuctionUpdateRequest(
        @Positive(message = "Start price must be greater than 0")
        @Digits(integer = 15, fraction = 0, message = "Start price must be an integer VND amount")
        BigDecimal startPrice,

        @Positive(message = "Bid increment must be greater than 0")
        @Digits(integer = 15, fraction = 0, message = "Bid increment must be an integer VND amount")
        BigDecimal bidIncrement,

        @Positive(message = "Reserve price must be greater than 0")
        @Digits(integer = 15, fraction = 0, message = "Reserve price must be an integer VND amount")
        BigDecimal reservePrice,

        Instant startTime,

        Instant endTime
) {}

