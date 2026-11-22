package com.omnibid.controller.admin;

import com.omnibid.common.ApiResponse;
import com.omnibid.common.PageResponse;
import com.omnibid.dto.admin.AdminAuctionDetailResponse;
import com.omnibid.dto.admin.AdminAuctionItemResponse;
import com.omnibid.dto.admin.AdminCancelAuctionRequest;
import com.omnibid.enums.AuctionStatus;
import com.omnibid.security.CurrentUser;
import com.omnibid.security.CustomUserDetails;
import com.omnibid.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/auctions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuctionController {

    private final AdminService adminService;

    @GetMapping
    public ApiResponse<PageResponse<AdminAuctionItemResponse>> getAuctions(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) AuctionStatus status,
            @RequestParam(required = false) Long sellerId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.ok(adminService.getAuctions(CurrentUser.of(userDetails), keyword, status, sellerId, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminAuctionDetailResponse> getAuctionById(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(adminService.getAuctionById(CurrentUser.of(userDetails), id));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<AdminAuctionDetailResponse> cancelAuction(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody AdminCancelAuctionRequest request
    ) {
        return ApiResponse.ok(adminService.cancelAuction(CurrentUser.of(userDetails), id, request));
    }
}
