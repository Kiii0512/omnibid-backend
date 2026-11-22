package com.omnibid.service;

import com.omnibid.common.PageResponse;
import com.omnibid.dto.admin.*;
import com.omnibid.dto.auction.AuctionResponse;
import com.omnibid.dto.order.OrderResponse;
import com.omnibid.enums.OrderStatus;
import com.omnibid.enums.ProductStatus;
import com.omnibid.enums.UserStatus;
import com.omnibid.security.CurrentUser;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Map;

public interface AdminService {

    UserStatusUpdateResponse updateUserStatus(CurrentUser admin, Long userId, UserStatusUpdateRequest request);

    PageResponse<AdminUserResponse> getUsers(CurrentUser admin, UserStatus status, String q, Pageable pageable);

    ProductHideResponse hideProduct(CurrentUser admin, Long productId, ProductHideRequest request);

    PageResponse<AdminProductResponse> getProducts(CurrentUser admin, ProductStatus status, Long sellerId, Long categoryId, Pageable pageable);

    AuctionResponse cancelAuction(CurrentUser admin, Long auctionId, AdminCancelAuctionRequest request);

    OrderResponse cancelOrder(CurrentUser admin, Long orderId, AdminCancelOrderRequest request);

    PageResponse<AdminOrderSummaryResponse> getOrders(CurrentUser admin, OrderStatus status, Long buyerId, Long sellerId, Instant from, Instant to, Pageable pageable);

    Map<String, Object> banUser(Long userId, String reason, Long adminId);

    Map<String, Object> hideProduct(Long productId, String reason, Long adminId);

    Map<String, Object> cancelAuction(Long auctionId, String reason, Long adminId);

    Map<String, Object> cancelOrder(Long orderId, String reason, Long adminId);

    AdminStatisticsResponse getStatistics(CurrentUser admin, Instant from, Instant to);
}
