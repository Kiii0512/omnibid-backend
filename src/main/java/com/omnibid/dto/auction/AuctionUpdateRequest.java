package com.omnibid.dto.auction;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;

public record AuctionUpdateRequest(
        @Positive(message = "Giá khởi điểm phải lớn hơn 0")
        @Digits(integer = 15, fraction = 0, message = "Giá khởi điểm phải là số nguyên")
        BigDecimal startPrice,

        @Positive(message = "Bước giá phải lớn hơn 0")
        @Digits(integer = 15, fraction = 0, message = "Bước giá phải là số nguyên")
        BigDecimal bidIncrement,

        @Digits(integer = 15, fraction = 0, message = "Giá sàn phải là số nguyên")
        BigDecimal reservePrice,

        Instant startTime,
        Instant endTime
) {}
