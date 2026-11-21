package com.omnibid.service;

import com.omnibid.common.PageResponse;
import com.omnibid.dto.bid.BidHistoryItem;
import com.omnibid.dto.bid.BidRequest;
import com.omnibid.dto.bid.MyBidResponse;
import com.omnibid.dto.bid.PlaceBidResponse;
import org.springframework.data.domain.Pageable;

public interface BidService {

    PlaceBidResponse placeBid(Long bidderId, Long auctionId, BidRequest request);

    PageResponse<BidHistoryItem> getBidsByAuction(Long auctionId, Pageable pageable);

    PageResponse<MyBidResponse> getMyBids(Long bidderId, Long auctionId, Pageable pageable);
}
