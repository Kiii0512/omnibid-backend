package com.omnibid.dto.bid;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BidResponse {

    private Long id;

    private Long auctionId;

    private Long bidderId;

    private String bidderName;

    private BigDecimal amount;

    private Instant createdAt;
}

