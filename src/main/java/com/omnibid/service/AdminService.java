package com.omnibid.service;

import com.omnibid.common.PageResponse;
import com.omnibid.dto.admin.*;
import com.omnibid.dto.order.OrderSummaryResponse;
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

    AdminUnbanUserResponse unbanUser(CurrentUser admin, Long userId, String reason);

    PageResponse<AdminProductItemResponse> getProducts(CurrentUser admin, String keyword, Long categoryId, ProductStatus status, Pageable pageable);

    AdminProductDetailResponse getProductById(CurrentUser admin, Long productId);

    ProductHideResponse hideProduct(CurrentUser admin, Long productId, String reason);

    PageResponse<AdminAuctionItemResponse> getAuctions(CurrentUser admin, String keyword, AuctionStatus status, Long sellerId, Pageable pageable);

    AdminAuctionDetailResponse getAuctionById(CurrentUser admin, Long auctionId);

    AdminAuctionDetailResponse cancelAuction(CurrentUser admin, Long auctionId, AdminCancelAuctionRequest request);

    PageResponse<OrderSummaryResponse> getOrders(CurrentUser admin, OrderStatus status, Long buyerId, Long sellerId, Instant fromDate, Instant toDate, Pageable pageable);

    AdminOrderDetailResponse getOrderById(CurrentUser admin, Long orderId);

    AdminOrderDetailResponse cancelOrder(CurrentUser admin, Long orderId, AdminCancelOrderRequest request);

    AdminStatisticsResponse getStatistics(CurrentUser admin);
}
