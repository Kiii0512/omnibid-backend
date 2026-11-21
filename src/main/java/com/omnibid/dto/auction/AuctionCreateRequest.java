package com.omnibid.dto.auction;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;

public record AuctionCreateRequest(
        @NotNull(message = "Mã sản phẩm là bắt buộc")
        Long productId,

        @NotNull(message = "Giá khởi điểm là bắt buộc")
        @Positive(message = "Giá khởi điểm phải lớn hơn 0")
        @Digits(integer = 15, fraction = 0, message = "Giá khởi điểm phải là số nguyên")
        BigDecimal startPrice,

        @NotNull(message = "Bước giá là bắt buộc")
        @Positive(message = "Bước giá phải lớn hơn 0")
        @Digits(integer = 15, fraction = 0, message = "Bước giá phải là số nguyên")
        BigDecimal bidIncrement,

        @Digits(integer = 15, fraction = 0, message = "Giá sàn phải là số nguyên")
        BigDecimal reservePrice,

        @NotNull(message = "Thời gian bắt đầu là bắt buộc")
        Instant startTime,

        @NotNull(message = "Thời gian kết thúc là bắt buộc")
        Instant endTime
) {}
