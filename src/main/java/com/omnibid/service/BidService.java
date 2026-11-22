package com.omnibid.service;

import com.omnibid.common.PageResponse;
import com.omnibid.dto.bid.BidHistoryItem;
import com.omnibid.dto.bid.BidRequest;
import com.omnibid.dto.bid.MyBidItem;
import com.omnibid.dto.bid.PlaceBidResponse;
import com.omnibid.security.CurrentUser;
import org.springframework.data.domain.Pageable;

public interface BidService {

    PlaceBidResponse placeBid(Long auctionId, Long bidderId, String bidderFullName, BidRequest request);

    PageResponse<BidHistoryItem> getBidsByAuction(Long auctionId, CurrentUser currentUser, Pageable pageable);

    PageResponse<MyBidItem> getMyBids(Long bidderId, Long auctionId, Pageable pageable);
}
