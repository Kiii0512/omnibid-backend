package com.omnibid.controller;

import com.omnibid.common.ApiResponse;
import com.omnibid.common.PageResponse;
import com.omnibid.dto.bid.BidHistoryItem;
import com.omnibid.dto.bid.BidRequest;
import com.omnibid.dto.bid.MyBidItem;
import com.omnibid.dto.bid.PlaceBidResponse;
import com.omnibid.security.CurrentUser;
import com.omnibid.security.CustomUserDetails;
import com.omnibid.service.BidService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class BidController {

    private final BidService bidService;

    @PostMapping("/api/v1/auctions/{id}/bids")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<PlaceBidResponse> placeBid(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody BidRequest request
    ) {
        CurrentUser currentUser = CurrentUser.of(userDetails);
        PlaceBidResponse response = bidService.placeBid(id, currentUser.id(), userDetails.getFullName(), request);
        return ApiResponse.ok(response);
    }

    @GetMapping("/api/v1/auctions/{id}/bids")
    public ApiResponse<PageResponse<BidHistoryItem>> getBidsByAuction(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        CurrentUser currentUser = userDetails != null ? CurrentUser.of(userDetails) : null;
        return ApiResponse.ok(bidService.getBidsByAuction(id, currentUser, pageable));
    }

    @GetMapping("/api/v1/bids/mine")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<PageResponse<MyBidItem>> getMyBids(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) Long auctionId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        CurrentUser currentUser = CurrentUser.of(userDetails);
        return ApiResponse.ok(bidService.getMyBids(currentUser.id(), auctionId, pageable));
    }
}
