package com.omnibid.service;

import com.omnibid.common.PageResponse;
import com.omnibid.dto.auction.AuctionResponse;
import com.omnibid.dto.watchlist.WatchlistSummaryResponse;
import org.springframework.data.domain.Pageable;

public interface WatchlistService {

    void addToWatchlist(Long userId, Long auctionId);

    void removeFromWatchlist(Long userId, Long auctionId);

    PageResponse<AuctionResponse> getMyWatchlist(Long userId, Pageable pageable);

    WatchlistSummaryResponse getWatchlistSummary(Long userId);
}

