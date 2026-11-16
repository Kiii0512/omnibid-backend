package com.omnibid.service;

import com.omnibid.common.PageResponse;
import com.omnibid.dto.auction.AuctionCreateRequest;
import com.omnibid.dto.auction.AuctionResponse;
import com.omnibid.dto.auction.AuctionUpdateRequest;
import com.omnibid.enums.AuctionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

public interface AuctionService {

    AuctionResponse createAuction(Long userId, AuctionCreateRequest request);

    AuctionResponse getAuctionById(Long id);

    AuctionResponse getAuctionById(Long id, Long currentUserId);

    Page<AuctionResponse> getAuctions(AuctionStatus status, Pageable pageable);

    PageResponse<AuctionResponse> searchAuctions(
            Long currentUserId,
            String q,
            List<Long> categoryIds,
            AuctionStatus status,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Long sellerId,
            String sort,
            Pageable pageable
    );

    AuctionResponse updateAuction(Long sellerId, Long auctionId, AuctionUpdateRequest request);

    PageResponse<AuctionResponse> getMyAuctions(Long sellerId, AuctionStatus status, Pageable pageable);

    AuctionResponse cancelAuction(Long userId, Long auctionId, String reason);

    void startScheduledAuctions();

    void closeEndedAuctions();
}
