package com.omnibid.controller;

import com.omnibid.common.ApiResponse;
import com.omnibid.common.PageResponse;
import com.omnibid.dto.auction.AuctionCancelRequest;
import com.omnibid.dto.auction.AuctionCreateRequest;
import com.omnibid.dto.auction.AuctionDetailResponse;
import com.omnibid.dto.auction.AuctionResponse;
import com.omnibid.dto.auction.AuctionUpdateRequest;
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
import java.time.Instant;

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
        return ApiResponse.success(response);
    }

    @GetMapping
    public ApiResponse<PageResponse<AuctionResponse>> searchAuctions(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) AuctionStatus status,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Instant endBefore,
            @PageableDefault(size = 10) Pageable pageable
    ) {
        PageResponse<AuctionResponse> page = auctionService.searchAuctions(
                q, categoryId, status, minPrice, maxPrice, endBefore, pageable
        );
        return ApiResponse.success(page);
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<PageResponse<AuctionResponse>> getMyAuctions(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) AuctionStatus status,
            @PageableDefault(size = 10) Pageable pageable
    ) {
        CurrentUser currentUser = CurrentUser.of(userDetails);
        PageResponse<AuctionResponse> page = auctionService.getMyAuctions(currentUser.id(), keyword, status, pageable);
        return ApiResponse.success(page);
    }

    @GetMapping("/{id}")
    public ApiResponse<AuctionDetailResponse> getAuctionById(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Long currentUserId = userDetails != null ? userDetails.getId() : null;
        AuctionDetailResponse response = auctionService.getAuctionById(id, currentUserId);
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
        return ApiResponse.success(response);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<AuctionResponse> cancelAuction(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody AuctionCancelRequest request
    ) {
        CurrentUser currentUser = CurrentUser.of(userDetails);
        AuctionResponse response = auctionService.cancelAuction(currentUser.id(), id, request.reason());
        return ApiResponse.success(response);
    }
}
