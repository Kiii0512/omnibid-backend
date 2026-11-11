package com.omnibid.service;

import com.omnibid.dto.auction.AuctionCreateRequest;
import com.omnibid.dto.auction.AuctionDetailResponse;
import com.omnibid.dto.auction.AuctionFilterRequest;
import com.omnibid.dto.auction.AuctionResponse;
import com.omnibid.enums.AuctionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AuctionService {

    AuctionResponse createAuction(Long userId, AuctionCreateRequest request);

    AuctionDetailResponse getAuctionById(Long id, Long currentUserId, boolean isAdmin);

    default AuctionDetailResponse getAuctionById(Long id) {
        return getAuctionById(id, null, false);
    }

    Page<AuctionResponse> getAuctions(AuctionFilterRequest filter, Pageable pageable);

    default Page<AuctionResponse> getAuctions(AuctionStatus status, Pageable pageable) {
        return getAuctions(AuctionFilterRequest.builder().status(status).build(), pageable);
    }

    AuctionResponse cancelAuction(Long userId, Long auctionId, String reason);

    void startScheduledAuctions();

    void closeEndedAuctions();
}
