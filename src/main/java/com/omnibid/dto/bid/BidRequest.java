package com.omnibid.dto.bid;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BidRequest {

    @NotNull(message = "Auction ID cannot be null")
    private Long auctionId;

    @NotNull(message = "Bid amount cannot be null")
    @DecimalMin(value = "1.00", message = "Bid amount must be at least 1.00")
    private BigDecimal amount;
}

