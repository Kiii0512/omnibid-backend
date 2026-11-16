package com.omnibid.service;

import com.omnibid.common.PageResponse;
import com.omnibid.dto.bid.BidRequest;
import com.omnibid.dto.bid.BidResponse;
import com.omnibid.dto.bid.BidSummaryResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface BidService {

    BidResponse placeBid(Long bidderId, BidRequest request);

    List<BidResponse> getBidsByAuction(Long auctionId);

    PageResponse<BidResponse> getMyBids(Long bidderId, Pageable pageable);

    BidSummaryResponse getMyBidSummary(Long bidderId);
}
