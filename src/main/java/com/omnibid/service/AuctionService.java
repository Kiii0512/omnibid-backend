package com.omnibid.service;

import com.omnibid.common.PageResponse;
import com.omnibid.dto.auction.AuctionCreateRequest;
import com.omnibid.dto.auction.AuctionDetailResponse;
import com.omnibid.dto.auction.AuctionResponse;
import com.omnibid.dto.auction.AuctionUpdateRequest;
import com.omnibid.enums.AuctionStatus;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;

public interface AuctionService {

    AuctionResponse createAuction(Long sellerId, AuctionCreateRequest request);

    PageResponse<AuctionResponse> searchAuctions(
            String q,
            Long categoryId,
            AuctionStatus status,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Instant endBefore,
            Pageable pageable
    );

    AuctionDetailResponse getAuctionById(Long id, Long currentUserId);

    AuctionResponse updateAuction(Long sellerId, Long id, AuctionUpdateRequest request);

    AuctionResponse cancelAuction(Long sellerId, Long id, String reason);

    PageResponse<AuctionResponse> getMyAuctions(Long sellerId, String keyword, AuctionStatus status, Pageable pageable);

    void startScheduledAuctions();

    void closeEndedAuctions();
}
