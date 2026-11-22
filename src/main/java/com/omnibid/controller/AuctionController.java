package com.omnibid.controller;

import com.omnibid.common.ApiResponse;
import com.omnibid.common.PageResponse;
import com.omnibid.dto.auction.AuctionCreateRequest;
import com.omnibid.dto.auction.AuctionDetailResponse;
import com.omnibid.dto.auction.AuctionSummaryResponse;
import com.omnibid.dto.auction.AuctionUpdateRequest;
import com.omnibid.dto.auction.CancelAuctionRequest;
import com.omnibid.dto.auction.MyAuctionItemResponse;
import com.omnibid.enums.AuctionStatus;
import com.omnibid.security.CurrentUser;
import com.omnibid.security.CustomUserDetails;
import com.omnibid.service.AuctionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
    public ApiResponse<AuctionDetailResponse> createAuction(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody AuctionCreateRequest request
    ) {
        CurrentUser currentUser = CurrentUser.of(userDetails);
        return ApiResponse.ok(auctionService.createAuction(currentUser.id(), request));
    }

    @GetMapping
    public ApiResponse<PageResponse<AuctionSummaryResponse>> getAuctions(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) AuctionStatus status,
            @RequestParam(required = false) Instant endBefore,
            @PageableDefault(size = 10, sort = "endTime", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return ApiResponse.ok(auctionService.searchAuctions(
                keyword, categoryId, minPrice, maxPrice, status, endBefore, pageable
        ));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<PageResponse<MyAuctionItemResponse>> getMyAuctions(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) AuctionStatus status,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        CurrentUser currentUser = CurrentUser.of(userDetails);
        return ApiResponse.ok(auctionService.getMyAuctions(currentUser.id(), status, keyword, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<AuctionDetailResponse> getAuctionById(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CurrentUser currentUser = userDetails != null ? CurrentUser.of(userDetails) : null;
        return ApiResponse.ok(auctionService.getAuctionById(id, currentUser));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<AuctionDetailResponse> updateAuction(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody AuctionUpdateRequest request
    ) {
        CurrentUser currentUser = CurrentUser.of(userDetails);
        return ApiResponse.ok(auctionService.updateAuction(currentUser.id(), id, request));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<AuctionDetailResponse> cancelAuction(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CancelAuctionRequest request
    ) {
        CurrentUser currentUser = CurrentUser.of(userDetails);
        return ApiResponse.ok(auctionService.cancelAuction(currentUser.id(), id, request.getReason()));
    }
}
