package com.omnibid.dto.auction;

import com.omnibid.enums.AuctionStatus;
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
public class AuctionResponse {

    private Long id;

    private Long productId;

    private String productTitle;

    private Long sellerId;

    private String sellerName;

    private BigDecimal startPrice;

    private BigDecimal bidIncrement;

    private BigDecimal currentPrice;

    private Long highestBidderId;

    private String highestBidderName;

    private Integer bidCount;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private AuctionStatus status;

    private Long winnerId;

    private BigDecimal finalPrice;
}

