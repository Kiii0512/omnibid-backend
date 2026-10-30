package com.omnibid.controller;

import com.omnibid.common.ApiResponse;
import com.omnibid.common.AppException;
import com.omnibid.dto.auction.AuctionCreateRequest;
import com.omnibid.dto.auction.AuctionResponse;
import com.omnibid.dto.auction.CancelAuctionRequest;
import com.omnibid.enums.AuctionStatus;
import com.omnibid.security.CustomUserDetails;
import com.omnibid.service.AuctionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auctions")
@RequiredArgsConstructor
public class AuctionController {

    private final AuctionService auctionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<AuctionResponse> createAuction(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody AuctionCreateRequest request
    ) {
        if (userDetails == null) {
            throw new AppException("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        AuctionResponse response = auctionService.createAuction(userDetails.getId(), request);
        return ApiResponse.success("Auction created successfully", response);
    }

    @GetMapping
    public ApiResponse<Page<AuctionResponse>> getAuctions(
            @RequestParam(required = false) AuctionStatus status,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<AuctionResponse> response = auctionService.getAuctions(status, pageable);
        return ApiResponse.success(response);
    }

    @GetMapping("/{id}")
    public ApiResponse<AuctionResponse> getAuctionById(@PathVariable Long id) {
        AuctionResponse response = auctionService.getAuctionById(id);
        return ApiResponse.success(response);
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<AuctionResponse> cancelAuction(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody(required = false) CancelAuctionRequest request
    ) {
        if (userDetails == null) {
            throw new AppException("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        String reason = request != null ? request.getReason() : null;
        AuctionResponse response = auctionService.cancelAuction(userDetails.getId(), id, reason);
        return ApiResponse.success("Auction cancelled successfully", response);
    }
}

