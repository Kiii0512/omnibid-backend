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

import java.time.LocalDateTime;
import java.util.Map;

public interface AdminService {

    // User management (ADM-01, 02..06)
    UserStatusUpdateResponse updateUserStatus(CurrentUser admin, Long userId, UserStatusUpdateRequest request);

    PageResponse<AdminUserResponse> getUsers(CurrentUser admin, UserStatus status, String q, Pageable pageable);

    // Product management (ADM-07, 08)
    ProductHideResponse hideProduct(CurrentUser admin, Long productId, ProductHideRequest request);

    PageResponse<AdminProductResponse> getProducts(CurrentUser admin, ProductStatus status, Long sellerId, Long categoryId, Pageable pageable);

    // Auction management (ADM-09)
    AuctionResponse cancelAuction(CurrentUser admin, Long auctionId, AdminCancelAuctionRequest request);

    // Order management (ADM-11, 12)
    OrderResponse cancelOrder(CurrentUser admin, Long orderId, AdminCancelOrderRequest request);

    PageResponse<AdminOrderSummaryResponse> getOrders(CurrentUser admin, OrderStatus status, Long buyerId, Long sellerId, LocalDateTime from, LocalDateTime to, Pageable pageable);

    // Operations for ReportService & reused actions
    Map<String, Object> banUser(Long userId, String reason, Long adminId);

    Map<String, Object> hideProduct(Long productId, String reason, Long adminId);

    Map<String, Object> cancelAuction(Long auctionId, String reason, Long adminId);

    Map<String, Object> completeOrder(Long orderId, String note, Long adminId);

    Map<String, Object> cancelOrder(Long orderId, String reason, Long adminId);

    // Statistics & Audit logs (ADM-14, 15)
    AdminStatisticsResponse getStatistics(CurrentUser admin, LocalDateTime from, LocalDateTime to);

    PageResponse<AuditLogResponse> getAuditLogs(CurrentUser admin, Long adminId, String action, String targetType, Long targetId, LocalDateTime from, LocalDateTime to, Pageable pageable);
}
