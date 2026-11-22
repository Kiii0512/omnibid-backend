package com.omnibid.controller;

import com.omnibid.common.ApiResponse;
import com.omnibid.common.PageResponse;
import com.omnibid.dto.auction.AuctionCreateRequest;
import com.omnibid.dto.auction.AuctionResponse;
import com.omnibid.dto.auction.AuctionUpdateRequest;
import com.omnibid.dto.auction.CancelAuctionRequest;
import com.omnibid.enums.AuctionStatus;
import com.omnibid.security.CurrentUser;
import com.omnibid.security.CustomUserDetails;
import com.omnibid.service.AuctionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

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
        CurrentUser currentUser = CurrentUser.of(userDetails);
        AuctionResponse response = auctionService.createAuction(currentUser.id(), request);
        return ApiResponse.success("Auction created successfully", response);
    }

    @GetMapping
    public ApiResponse<PageResponse<AuctionResponse>> getAuctions(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<Long> categoryIds,
            @RequestParam(required = false) AuctionStatus status,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) String sort,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Long currentUserId = userDetails != null ? userDetails.getId() : null;
        PageResponse<AuctionResponse> page = auctionService.searchAuctions(
                currentUserId, q, categoryIds, status, minPrice, maxPrice, sellerId, sort, pageable
        );
        return ApiResponse.success(page);
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<PageResponse<AuctionResponse>> getMyAuctions(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) AuctionStatus status,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        CurrentUser currentUser = CurrentUser.of(userDetails);
        PageResponse<AuctionResponse> page = auctionService.getMyAuctions(currentUser.id(), status, pageable);
        return ApiResponse.success(page);
    }

    @GetMapping("/{id}")
    public ApiResponse<AuctionResponse> getAuctionById(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Long currentUserId = userDetails != null ? userDetails.getId() : null;
        AuctionResponse response = auctionService.getAuctionById(id, currentUserId);
        return ApiResponse.success(response);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<AuctionResponse> updateAuction(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody AuctionUpdateRequest request
    ) {
        CurrentUser currentUser = CurrentUser.of(userDetails);
        AuctionResponse response = auctionService.updateAuction(currentUser.id(), id, request);
        return ApiResponse.success("Auction updated successfully", response);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<AuctionResponse> cancelAuction(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody(required = false) CancelAuctionRequest request
    ) {
        CurrentUser currentUser = CurrentUser.of(userDetails);
        String reason = (request != null && request.getReason() != null) ? request.getReason() : "Cancelled by user";
        AuctionResponse response = auctionService.cancelAuction(currentUser.id(), id, reason);
        return ApiResponse.success("Auction cancelled successfully", response);
    }
}
