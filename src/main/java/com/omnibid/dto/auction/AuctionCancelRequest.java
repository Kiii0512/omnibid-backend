package com.omnibid.dto.auction;

import jakarta.validation.constraints.NotBlank;

public record AuctionCancelRequest(
        @NotBlank(message = "Lý do hủy phiên là bắt buộc")
        String reason
) {}
