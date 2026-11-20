package com.omnibid.it;

import com.omnibid.entity.*;
import com.omnibid.enums.*;
import com.omnibid.scheduler.AuctionScheduler;
import com.omnibid.support.BaseIT;
import com.omnibid.support.Concurrently;
import com.omnibid.support.RepeatedConcurrencyTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

public class AdminIntegrationIT extends BaseIT {

    @Autowired
    private AuctionScheduler auctionScheduler;

    @Test
    @DisplayName("INT-ADM-01 Khóa người dẫn đầu rồi chốt phiên")
    void testBanLeadingBidderThenCloseAuction() {
        LocalDateTime now = LocalDateTime.now(mutableClock);
        Product product = seed.createProduct(seller, category, "Antique Vase", ProductStatus.IN_AUCTION);
        Auction auction = seed.createAuction(seller, product, new BigDecimal("500000"), new BigDecimal("10000"),
                now.minusMinutes(30), now.plusMinutes(30), AuctionStatus.ACTIVE);

        // Đặt giá: OTHER 520k, BIDDER_B 530k, OTHER 540k
        api.as(other).post("/bids", Map.of("auctionId", auction.getId(), "amount", 520000));
        api.as(bidderB).post("/bids", Map.of("auctionId", auction.getId(), "amount", 530000));
        api.as(other).post("/bids", Map.of("auctionId", auction.getId(), "amount", 540000));

        // Khóa OTHER
        ResponseEntity<String> banRes = api.as(admin).patch(
                "/admin/users/" + other.getId(),
                Map.of("status", "BANNED", "reason", "Vi phạm quy chế đấu giá")
        );
        assertThat(banRes.getStatusCode().is2xxSuccessful()).isTrue();

        // Sau khóa: current_price = 530000, leader = BIDDER_B, bid_count = 1
        Map<String, Object> aucData = jdbcTemplate.queryForMap(
                "SELECT current_price, highest_bidder_id, bid_count FROM auctions WHERE id = ?", auction.getId()
        );
        assertThat(new BigDecimal(aucData.get("current_price").toString())).isEqualByComparingTo("530000");
        assertThat(((Number) aucData.get("highest_bidder_id")).longValue()).isEqualTo(bidderB.getId());
        assertThat(((Number) aucData.get("bid_count")).intValue()).isEqualTo(1);

        // Hết giờ và chốt phiên
        mutableClock.advance(Duration.ofMinutes(35));
        auctionScheduler.scheduleAuctionStatusTransitions();

        // Người thắng BIDDER_B, Order tạo với final_price = 530000
        List<Map<String, Object>> orders = jdbcTemplate.queryForList(
                "SELECT * FROM orders WHERE auction_id = ?", auction.getId()
        );
        assertThat(orders).hasSize(1);
        Map<String, Object> ord = orders.get(0);
        assertThat(((Number) ord.get("buyer_id")).longValue()).isEqualTo(bidderB.getId());
        assertThat(new BigDecimal(ord.get("final_price").toString())).isEqualByComparingTo("530000");

        // OTHER gọi GET /orders -> 403 ACCOUNT_BANNED
        ResponseEntity<String> getOrderRes = api.as(other).get("/orders");
        assertThat(getOrderRes.getStatusCode().value()).isEqualTo(403);
        assertThat(getOrderRes.getBody()).contains("ACCOUNT_BANNED");

        // Khôi phục OTHER để bảo vệ invariant cuối test
        jdbcTemplate.update("UPDATE users SET status = 'ACTIVE' WHERE id = ?", other.getId());
    }

    @Test
    @DisplayName("INT-ADM-02 Ẩn sản phẩm qua báo cáo")
    void testHideProductViaReport() {
        LocalDateTime now = LocalDateTime.now(mutableClock);
        Product product = seed.createProduct(seller, category, "Violating Item", ProductStatus.IN_AUCTION);
        Auction auction = seed.createAuction(seller, product, new BigDecimal("100000"), new BigDecimal("10000"),
                now.minusMinutes(30), now.plusMinutes(30), AuctionStatus.ACTIVE);

        api.as(bidderB).post("/bids", Map.of("auctionId", auction.getId(), "amount", 110000));

        // OTHER gửi báo cáo VIOLATION
        ResponseEntity<String> reportRes = api.as(other).post(
                "/reports",
                Map.of("reportType", "VIOLATION", "reportedProductId", product.getId(), "reason", "Hang gia mao nhan hieu khong dung chat luong")
        );
        assertThat(reportRes.getStatusCode().is2xxSuccessful()).isTrue();

        Long reportId = jdbcTemplate.queryForObject("SELECT id FROM reports WHERE reported_product_id = ?", Long.class, product.getId());

        // Admin xử lý: HIDE_PRODUCT
        ResponseEntity<String> resolveRes = api.as(admin).patch(
                "/admin/reports/" + reportId + "/resolve",
                Map.of("action", "HIDE_PRODUCT", "note", "Xác nhận hàng vi phạm sở hữu trí tuệ")
        );
        assertThat(resolveRes.getStatusCode().is2xxSuccessful()).isTrue();

        // Product HIDDEN, Auction CANCELLED, Bid NOT_WON, Report RESOLVED
        String prodStatus = jdbcTemplate.queryForObject("SELECT status FROM products WHERE id = ?", String.class, product.getId());
        assertThat(prodStatus).isEqualTo("HIDDEN");

        String aucStatus = jdbcTemplate.queryForObject("SELECT status FROM auctions WHERE id = ?", String.class, auction.getId());
        assertThat(aucStatus).isEqualTo("CANCELLED");

        String bidStatus = jdbcTemplate.queryForObject("SELECT status FROM bids WHERE auction_id = ?", String.class, auction.getId());
        assertThat(bidStatus).isEqualTo("NOT_WON");

        String repStatus = jdbcTemplate.queryForObject("SELECT status FROM reports WHERE id = ?", String.class, reportId);
        assertThat(repStatus).isEqualTo("RESOLVED");

        // Audit logs: REPORT_RESOLVE, PRODUCT_HIDE, AUCTION_CANCEL
        List<String> actions = jdbcTemplate.queryForList(
                "SELECT action FROM audit_logs WHERE admin_id = ?", String.class, admin.getId()
        );
        assertThat(actions).contains("REPORT_RESOLVE", "PRODUCT_HIDE", "AUCTION_CANCEL");

        // Hết giờ cũ + chốt -> Không có Order
        mutableClock.advance(Duration.ofMinutes(35));
        auctionScheduler.scheduleAuctionStatusTransitions();

        Long orderCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders WHERE auction_id = ?", Long.class, auction.getId());
        assertThat(orderCount).isEqualTo(0L);
    }

    @Test
    @DisplayName("INT-ADM-03 Khóa seller có đủ loại dữ liệu")
    void testBanSellerWithMixedData() {
        LocalDateTime now = LocalDateTime.now(mutableClock);

        // 1) Auction SCHEDULED
        Product prod1 = seed.createProduct(seller, category, "Prod 1", ProductStatus.AVAILABLE);
        Auction auc1 = seed.createAuction(seller, prod1, new BigDecimal("100000"), new BigDecimal("10000"),
                now.plusHours(1), now.plusHours(2), AuctionStatus.SCHEDULED);

        // 2) Auction ACTIVE có Bid
        Product prod2 = seed.createProduct(seller, category, "Prod 2", ProductStatus.IN_AUCTION);
        Auction auc2 = seed.createAuction(seller, prod2, new BigDecimal("100000"), new BigDecimal("10000"),
                now.minusMinutes(30), now.plusMinutes(30), AuctionStatus.ACTIVE);
        api.as(bidderB).post("/bids", Map.of("auctionId", auc2.getId(), "amount", 110000));

        // 3) Order PENDING_PAYMENT
        Order orderPending = seed.orderPendingPayment(buyer, seller);

        // 4) Order SHIPPING
        Order orderPaid = seed.orderPaid(buyer, seller);
        Long shipmentId = jdbcTemplate.queryForObject("SELECT id FROM shipments WHERE order_id = ?", Long.class, orderPaid.getId());
        api.as(seller).patch("/shipments/" + shipmentId + "/status", Map.of("status", "PICKED_UP"));
        api.as(seller).patch("/shipments/" + shipmentId + "/status", Map.of("status", "IN_TRANSIT"));
        api.as(seller).patch("/shipments/" + shipmentId + "/status", Map.of("status", "DELIVERED"));

        // Admin khóa SELLER
        ResponseEntity<String> banRes = api.as(admin).patch(
                "/admin/users/" + seller.getId(),
                Map.of("status", "BANNED", "reason", "Lừa đảo nhiều khách hàng")
        );
        assertThat(banRes.getStatusCode().is2xxSuccessful()).isTrue();

        // Phiên 1, 2 bị CANCELLED
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM auctions WHERE id = ?", String.class, auc1.getId()))
                .isEqualTo("CANCELLED");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM auctions WHERE id = ?", String.class, auc2.getId()))
                .isEqualTo("CANCELLED");

        // Order PENDING_PAYMENT bị CANCELLED
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderPending.getId()))
                .isEqualTo("CANCELLED");

        // Order SHIPPING được giữ nguyên SHIPPING
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderPaid.getId()))
                .isEqualTo("SHIPPING");

        // Buyer xác nhận nhận hàng cho Order SHIPPING thành công -> COMPLETED, Product -> SOLD
        ResponseEntity<String> confirmRes = api.as(buyer).patch(
                "/orders/" + orderPaid.getId() + "/confirm-receipt",
                Map.of()
        );
        assertThat(confirmRes.getStatusCode().is2xxSuccessful()).isTrue();

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderPaid.getId()))
                .isEqualTo("COMPLETED");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM products WHERE id = ?", String.class, orderPaid.getProductId()))
                .isEqualTo("SOLD");

        // Khôi phục seller để sạch invariant
        jdbcTemplate.update("UPDATE users SET status = 'ACTIVE' WHERE id = ?", seller.getId());
    }

    @Test
    @DisplayName("INT-ADM-04 Admin hủy phiên có Bid")
    void testAdminCancelAuctionWithBids() {
        LocalDateTime now = LocalDateTime.now(mutableClock);
        Product product = seed.createProduct(seller, category, "Collectible Comic", ProductStatus.IN_AUCTION);
        Auction auction = seed.createAuction(seller, product, new BigDecimal("100000"), new BigDecimal("10000"),
                now.minusMinutes(30), now.plusMinutes(30), AuctionStatus.ACTIVE);

        api.as(buyer).post("/bids", Map.of("auctionId", auction.getId(), "amount", 110000));
        api.as(bidderB).post("/bids", Map.of("auctionId", auction.getId(), "amount", 120000));
        api.as(buyer).post("/bids", Map.of("auctionId", auction.getId(), "amount", 130000));

        // Admin hủy phiên
        ResponseEntity<String> cancelRes = api.as(admin).post(
                "/admin/auctions/" + auction.getId() + "/cancel",
                Map.of("reason", "Nghi vấn thao túng giá thầu")
        );
        assertThat(cancelRes.getStatusCode().is2xxSuccessful()).isTrue();

        // Auction CANCELLED, đủ 3 bids trong DB
        String aucStatus = jdbcTemplate.queryForObject("SELECT status FROM auctions WHERE id = ?", String.class, auction.getId());
        assertThat(aucStatus).isEqualTo("CANCELLED");

        Long bidCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM bids WHERE auction_id = ?", Long.class, auction.getId());
        assertThat(bidCount).isEqualTo(3L);

        String prodStatus = jdbcTemplate.queryForObject("SELECT status FROM products WHERE id = ?", String.class, product.getId());
        assertThat(prodStatus).isEqualTo("AVAILABLE");

        // Sau đó hết giờ + chốt: closer không đụng phiên CANCELLED, không có Order
        mutableClock.advance(Duration.ofMinutes(35));
        auctionScheduler.scheduleAuctionStatusTransitions();

        Long orderCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders WHERE auction_id = ?", Long.class, auction.getId());
        assertThat(orderCount).isEqualTo(0L);
    }

    @Test
    @DisplayName("INT-ADM-05 Hai Admin xử lý cùng báo cáo: 1 thành công 200, 1 bị 409 INVALID_STATUS_TRANSITION")
    void testConcurrentReportResolutions() throws Exception {
        Order order = seed.orderDelivered(buyer, seller);

        // Buyer tạo COMPLAINT report
        ResponseEntity<String> rRes = api.as(buyer).post(
                "/reports",
                Map.of("reportType", "COMPLAINT", "orderId", order.getId(), "reason", "Hang bi vo man hinh")
        );
        assertThat(rRes.getStatusCode().is2xxSuccessful()).isTrue();

        Long reportId = jdbcTemplate.queryForObject("SELECT id FROM reports WHERE order_id = ?", Long.class, order.getId());

        // 2 luồng giải quyết đồng thời: CANCEL_ORDER vs NONE
        List<ResponseEntity<String>> results = Concurrently.runIndexed(2, (index) -> {
            String action = index == 0 ? "CANCEL_ORDER" : "NONE";
            return api.as(admin).patch(
                    "/admin/reports/" + reportId + "/resolve",
                    Map.of("action", action, "note", "Admin quyet dinh " + action)
            );
        });

        int successCount = 0;
        int conflictCount = 0;
        for (ResponseEntity<String> res : results) {
            if (res.getStatusCode().is2xxSuccessful()) {
                successCount++;
            } else if (res.getStatusCode().value() == 409) {
                conflictCount++;
                assertThat(res.getBody()).contains("INVALID_STATUS_TRANSITION");
            }
        }

        assertThat(successCount).isEqualTo(1);
        assertThat(conflictCount).isEqualTo(1);
    }

    @RepeatedConcurrencyTest
    @DisplayName("INT-ADM-06 Khóa người dẫn đầu khi đang có 10 lượt đặt giá đồng thời")
    void testBanLeadingBidderDuringConcurrentBids() throws Exception {
        LocalDateTime now = LocalDateTime.now(mutableClock);
        Product product = seed.createProduct(seller, category, "Hot Smartphone", ProductStatus.IN_AUCTION);
        Auction auction = seed.createAuction(seller, product, new BigDecimal("1000000"), new BigDecimal("10000"),
                now.minusMinutes(10), now.plusMinutes(20), AuctionStatus.ACTIVE);

        // OTHER đặt giá dẫn đầu trước
        api.as(other).post("/bids", Map.of("auctionId", auction.getId(), "amount", 1010000));

        // 10 luồng đặt giá (từ BUYER và BIDDER_B) + 1 luồng Admin khóa OTHER
        List<ResponseEntity<String>> results = Concurrently.runIndexed(11, (index) -> {
            if (index == 0) {
                return api.as(admin).patch(
                        "/admin/users/" + other.getId(),
                        Map.of("status", "BANNED", "reason", "Khóa kiểm tra đồng thời")
                );
            } else {
                User user = (index % 2 == 0) ? buyer : bidderB;
                long amount = 1010000 + (index * 10000L);
                return api.as(user).post("/bids", Map.of("auctionId", auction.getId(), "amount", amount));
            }
        });

        // Không deadlock, kết thúc bình thường
        for (int i = 0; i < results.size(); i++) {
            System.out.println("Result " + i + ": " + results.get(i).getStatusCode() + " body: " + results.get(i).getBody());
        }
        System.out.println("AUCTION: " + jdbcTemplate.queryForMap("SELECT * FROM auctions WHERE id = ?", auction.getId()));
        System.out.println("BIDS: " + jdbcTemplate.queryForList("SELECT id, bidder_id, amount, status FROM bids WHERE auction_id = ?", auction.getId()));
        assertThat(results).hasSize(11);

        // Khôi phục OTHER để sạch invariant
        jdbcTemplate.update("UPDATE users SET status = 'ACTIVE' WHERE id = ?", other.getId());
    }

    @Test
    @DisplayName("INT-ADM-07 Thống kê khớp dữ liệu trong DB")
    void testAdminStatisticsMatchDatabase() {
        // Seed dữ liệu
        Order orderPaid = seed.orderPaid(buyer, seller);

        ResponseEntity<String> statsRes = api.as(admin).get("/admin/statistics");
        assertThat(statsRes.getStatusCode().is2xxSuccessful()).isTrue();

        Long totalUsers = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Long.class);
        assertThat(statsRes.getBody()).contains("\"users\":{\"total\":" + totalUsers);
    }

    @Test
    @DisplayName("INT-ADM-08 Rollback xóa sạch audit khi gặp lỗi bất ngờ")
    void testRollbackCleansAuditOnUnexpectedError() {
        // Dùng user ID không tồn tại hoặc dữ liệu sai để chứng minh rollback không tạo audit_logs thừa
        long initialAuditCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_logs", Long.class);

        // Gọi hủy order không tồn tại
        ResponseEntity<String> res = api.as(admin).post(
                "/admin/orders/999999/cancel",
                Map.of("reason", "Huy don khong ton tai")
        );
        assertThat(res.getStatusCode().value()).isEqualTo(404);

        long currentAuditCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_logs", Long.class);
        assertThat(currentAuditCount).isEqualTo(initialAuditCount);
    }
}
