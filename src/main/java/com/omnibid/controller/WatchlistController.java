package com.omnibid.controller;

import com.omnibid.common.ApiResponse;
import com.omnibid.common.PageResponse;
import com.omnibid.dto.auction.AuctionResponse;
import com.omnibid.dto.watchlist.WatchlistSummaryResponse;
import com.omnibid.security.CurrentUser;
import com.omnibid.security.CustomUserDetails;
import com.omnibid.service.WatchlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/watchlist")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class WatchlistController {

    private final WatchlistService watchlistService;

    @PostMapping("/{auctionId}")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> addToWatchlist(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long auctionId
    ) {
        CurrentUser currentUser = CurrentUser.of(userDetails);
        watchlistService.addToWatchlist(currentUser.id(), auctionId);
        return ApiResponse.success("Added to watchlist", null);
    }

    @DeleteMapping("/{auctionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> removeFromWatchlist(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long auctionId
    ) {
        CurrentUser currentUser = CurrentUser.of(userDetails);
        watchlistService.removeFromWatchlist(currentUser.id(), auctionId);
        return ApiResponse.success("Removed from watchlist", null);
    }

    @GetMapping
    public ApiResponse<PageResponse<AuctionResponse>> getMyWatchlist(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        CurrentUser currentUser = CurrentUser.of(userDetails);
        PageResponse<AuctionResponse> response = watchlistService.getMyWatchlist(currentUser.id(), pageable);
        return ApiResponse.success(response);
    }

    @GetMapping("/summary")
    public ApiResponse<WatchlistSummaryResponse> getWatchlistSummary(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CurrentUser currentUser = CurrentUser.of(userDetails);
        WatchlistSummaryResponse response = watchlistService.getWatchlistSummary(currentUser.id());
        return ApiResponse.success(response);
    }
}

