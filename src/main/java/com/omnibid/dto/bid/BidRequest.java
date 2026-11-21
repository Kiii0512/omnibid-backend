package com.omnibid.dto.bid;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record BidRequest(
        Long auctionId,

        @NotNull(message = "Giá đặt là bắt buộc")
        @Positive(message = "Giá đặt phải lớn hơn 0")
        @Digits(integer = 15, fraction = 0, message = "Giá đặt phải là số nguyên")
        BigDecimal amount
) {
    public BidRequest(BigDecimal amount) {
        this(null, amount);
    }
}
