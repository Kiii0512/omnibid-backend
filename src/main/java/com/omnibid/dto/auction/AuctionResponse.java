package com.omnibid.dto.auction;

import com.omnibid.enums.AuctionStatus;
import com.omnibid.enums.ProductCondition;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class AuctionResponse {

    private Long id;

    private Long productId;

    private String productTitle;

    private String primaryImageUrl;

    private Long categoryId;

    private String categoryName;

    private ProductCondition condition;

    private Long sellerId;

    private String sellerName;

    private BigDecimal startPrice;

    private BigDecimal bidIncrement;

    private BigDecimal currentPrice;

    private BigDecimal minNextBid;

    private Boolean reserveReached;

    private Long highestBidderId;

    private String highestBidderName;

    private Integer bidCount;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private LocalDateTime serverTime;

    private AuctionStatus status;

    private Long winnerId;

    private BigDecimal finalPrice;
}
