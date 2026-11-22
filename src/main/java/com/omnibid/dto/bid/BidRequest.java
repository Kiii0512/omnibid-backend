package com.omnibid.dto.bid;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record BidRequest(
        @NotNull(message = "Giá đặt không được để trống")
        @Positive(message = "Giá đặt phải lớn hơn 0")
        @Digits(integer = 15, fraction = 0, message = "Giá đặt phải là số nguyên VND")
        BigDecimal amount
) {}
