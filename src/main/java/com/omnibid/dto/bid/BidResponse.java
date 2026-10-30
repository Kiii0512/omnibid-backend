package com.omnibid.dto.bid;

import com.omnibid.enums.BidStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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

    private BidStatus status;

    private LocalDateTime createdAt;
}

