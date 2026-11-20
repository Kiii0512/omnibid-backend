package com.omnibid.controller;

import com.omnibid.common.ApiResponse;
import com.omnibid.common.PageResponse;
import com.omnibid.dto.bid.BidRequest;
import com.omnibid.dto.bid.BidResponse;
import com.omnibid.dto.bid.BidSummaryResponse;
import com.omnibid.security.CurrentUser;
import com.omnibid.security.CustomUserDetails;
import com.omnibid.service.BidService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class BidController {

    private final BidService bidService;

    @PostMapping("/api/v1/bids")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<BidResponse> placeBid(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody BidRequest request) {
        CurrentUser currentUser = CurrentUser.of(userDetails);
        BidResponse response = bidService.placeBid(currentUser.id(), request);
        return ApiResponse.success("Bid placed successfully", response);
    }

    @GetMapping("/api/v1/auctions/{auctionId}/bids")
    public ApiResponse<List<BidResponse>> getBidsByAuction(@PathVariable Long auctionId) {
        List<BidResponse> response = bidService.getBidsByAuction(auctionId);
        return ApiResponse.success(response);
    }

    @GetMapping("/api/v1/bids/mine")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<PageResponse<BidResponse>> getMyBids(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PageableDefault(size = 20) Pageable pageable) {
        CurrentUser currentUser = CurrentUser.of(userDetails);
        PageResponse<BidResponse> response = bidService.getMyBids(currentUser.id(), pageable);
        return ApiResponse.success(response);
    }

    @GetMapping("/api/v1/bids/mine/summary")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<BidSummaryResponse> getMyBidSummary(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        CurrentUser currentUser = CurrentUser.of(userDetails);
        BidSummaryResponse response = bidService.getMyBidSummary(currentUser.id());
        return ApiResponse.success(response);
    }
}
