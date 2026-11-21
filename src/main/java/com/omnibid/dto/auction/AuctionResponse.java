package com.omnibid.dto.auction;

import com.omnibid.dto.product.ProductImageResponse;
import com.omnibid.enums.AuctionStatus;
import com.omnibid.enums.ProductCondition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuctionResponse {

    private Long id;

    private Long productId;

    private String productTitle;

    private String productDescription;

    private Long categoryId;

    private String categoryName;

    private ProductCondition condition;

    private Long sellerId;

    private String sellerName;

    private BigDecimal startPrice;

    private BigDecimal bidIncrement;

    private BigDecimal currentPrice;

    private BigDecimal minNextBid;

    private String reserveStatus;

    private Long highestBidderId;

    private String highestBidderName;

    private Integer bidCount;

    private Instant startTime;

    private Instant endTime;

    private AuctionStatus status;

    private Long winnerId;

    private BigDecimal finalPrice;

    private Instant closedAt;

    private String cancelReason;

    private Instant createdAt;

    private String primaryImageUrl;

    private List<ProductImageResponse> images;

    private Instant serverTime;

    private Boolean isWatched;
}
