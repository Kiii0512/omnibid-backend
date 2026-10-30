package com.omnibid.service;

import com.omnibid.dto.auction.AuctionCreateRequest;
import com.omnibid.dto.auction.AuctionResponse;
import com.omnibid.enums.AuctionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AuctionService {

    AuctionResponse createAuction(Long userId, AuctionCreateRequest request);

    AuctionResponse getAuctionById(Long id);

    Page<AuctionResponse> getAuctions(AuctionStatus status, Pageable pageable);

    AuctionResponse cancelAuction(Long userId, Long auctionId, String reason);

    void startScheduledAuctions();

    void closeEndedAuctions();
}

