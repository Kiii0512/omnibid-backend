package com.omnibid.controller;

import com.omnibid.common.ApiResponse;
import com.omnibid.common.AppException;
import com.omnibid.dto.bid.BidRequest;
import com.omnibid.dto.bid.BidResponse;
import com.omnibid.security.CustomUserDetails;
import com.omnibid.service.BidService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
            @Valid @RequestBody BidRequest request
    ) {
        if (userDetails == null) {
            throw new AppException("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        BidResponse response = bidService.placeBid(userDetails.getId(), request);
        return ApiResponse.success("Bid placed successfully", response);
    }

    @GetMapping("/api/v1/auctions/{auctionId}/bids")
    public ApiResponse<List<BidResponse>> getBidsByAuction(@PathVariable Long auctionId) {
        List<BidResponse> response = bidService.getBidsByAuction(auctionId);
        return ApiResponse.success(response);
    }
}

