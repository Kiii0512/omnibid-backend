package com.omnibid.service;

import com.omnibid.dto.bid.BidRequest;
import com.omnibid.dto.bid.BidResponse;

import java.util.List;

public interface BidService {

    BidResponse placeBid(Long bidderId, BidRequest request);

    List<BidResponse> getBidsByAuction(Long auctionId);
}

