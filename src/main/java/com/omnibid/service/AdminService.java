package com.omnibid.service;

import java.util.Map;

public interface AdminService {

    Map<String, Object> banUser(Long userId, String reason, Long adminId);

    Map<String, Object> hideProduct(Long productId, String reason, Long adminId);

    Map<String, Object> cancelAuction(Long auctionId, String reason, Long adminId);

    Map<String, Object> completeOrder(Long orderId, String note, Long adminId);

    Map<String, Object> cancelOrder(Long orderId, String reason, Long adminId);
}
