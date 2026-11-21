package com.omnibid.service;

import com.omnibid.common.PageResponse;
import com.omnibid.dto.admin.*;
import com.omnibid.dto.auction.AuctionDetailResponse;
import com.omnibid.dto.auction.AuctionResponse;
import com.omnibid.dto.order.OrderResponse;
import com.omnibid.dto.product.ProductResponse;
import com.omnibid.enums.AuctionStatus;
import com.omnibid.enums.OrderStatus;
import com.omnibid.enums.ProductStatus;
import com.omnibid.enums.UserRole;
import com.omnibid.enums.UserStatus;
import com.omnibid.security.CurrentUser;
import org.springframework.data.domain.Pageable;

import java.time.Instant;

public interface AdminService {

    PageResponse<AdminUserResponse> getUsers(CurrentUser admin, String keyword, UserStatus status, UserRole role, Pageable pageable);

    AdminBanUserResponse banUser(CurrentUser admin, Long userId, String reason);

    AdminUserResponse unbanUser(CurrentUser admin, Long userId, String reason);

    PageResponse<AdminProductResponse> getProducts(CurrentUser admin, String keyword, Long categoryId, ProductStatus status, Pageable pageable);

    ProductResponse getProductById(CurrentUser admin, Long productId);

    ProductHideResponse hideProduct(CurrentUser admin, Long productId, String reason);

    PageResponse<AuctionResponse> getAuctions(CurrentUser admin, String keyword, AuctionStatus status, Long sellerId, Pageable pageable);

    AuctionDetailResponse getAuctionById(CurrentUser admin, Long auctionId);

    AuctionResponse cancelAuction(CurrentUser admin, Long auctionId, String reason);

    PageResponse<AdminOrderSummaryResponse> getOrders(CurrentUser admin, OrderStatus status, Long buyerId, Long sellerId, Instant from, Instant to, Pageable pageable);

    AdminOrderDetailResponse getOrderById(CurrentUser admin, Long orderId);

    OrderResponse cancelOrder(CurrentUser admin, Long orderId, String reason);

    AdminStatisticsResponse getStatistics(CurrentUser admin);
}
