package com.omnibid.service;

import com.omnibid.common.PageResponse;
import com.omnibid.dto.auction.AuctionCreateRequest;
import com.omnibid.dto.auction.AuctionDetailResponse;
import com.omnibid.dto.auction.AuctionSummaryResponse;
import com.omnibid.dto.auction.AuctionUpdateRequest;
import com.omnibid.dto.auction.MyAuctionItemResponse;
import com.omnibid.enums.AuctionStatus;
import com.omnibid.security.CurrentUser;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;

public interface AuctionService {

    AuctionDetailResponse createAuction(Long sellerId, AuctionCreateRequest request);

    AuctionDetailResponse getAuctionById(Long id, CurrentUser currentUser);

    PageResponse<AuctionSummaryResponse> searchAuctions(
            String keyword,
            Long categoryId,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            AuctionStatus status,
            Instant endBefore,
            Pageable pageable
    );

    AuctionDetailResponse updateAuction(Long sellerId, Long auctionId, AuctionUpdateRequest request);

    PageResponse<MyAuctionItemResponse> getMyAuctions(
            Long sellerId,
            AuctionStatus status,
            String keyword,
            Pageable pageable
    );

    AuctionDetailResponse cancelAuction(Long sellerId, Long auctionId, String reason);

    void startScheduledAuctions();

    void closeEndedAuctions();
}
