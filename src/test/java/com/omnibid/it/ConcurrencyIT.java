package com.omnibid.it;

import com.omnibid.entity.Auction;
import com.omnibid.entity.Order;
import com.omnibid.entity.Product;
import com.omnibid.entity.User;
import com.omnibid.enums.*;
import com.omnibid.scheduler.AuctionScheduler;
import com.omnibid.scheduler.OrderScheduler;
import com.omnibid.support.BaseIT;
import com.omnibid.support.Concurrently;
import com.omnibid.support.CountingMockPaymentGateway;
import com.omnibid.support.RepeatedConcurrencyTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

public class ConcurrencyIT extends BaseIT {

    @Autowired
    private AuctionScheduler auctionScheduler;

    @Autowired
    private OrderScheduler orderScheduler;

    @Autowired
    private CountingMockPaymentGateway gateway;

    @BeforeEach
    void resetGateway() {
        gateway.reset();
    }

    @RepeatedConcurrencyTest
    @DisplayName("INT-CON-01 Hai người đặt cùng mức (R-01): 1 thành công, 1 lỗi giá thấp kèm minNextBid")
    void testTwoBiddersSameAmount() throws Exception {
        LocalDateTime now = LocalDateTime.now(mutableClock);
        Product product = seed.createProduct(seller, category, "Gold Coin", ProductStatus.IN_AUCTION);
        Auction auction = seed.createAuction(seller, product, new BigDecimal("500000"), new BigDecimal("10000"),
                now.minusMinutes(10), now.plusMinutes(20), AuctionStatus.ACTIVE);

        List<ResponseEntity<String>> results = Concurrently.runIndexed(2, (index) -> {
            User bidder = index == 0 ? buyer : bidderB;
            return api.as(bidder).post("/bids", Map.of("auctionId", auction.getId(), "amount", 510000));
        });

        int successCount = 0;
        int failedCount = 0;
        for (ResponseEntity<String> res : results) {
            if (res.getStatusCode().is2xxSuccessful()) {
                successCount++;
            } else if (res.getStatusCode().value() == 409) {
                failedCount++;
                assertThat(res.getBody()).contains("BID_TOO_LOW");
                assertThat(res.getBody()).contains("520000");
            }
        }

        assertThat(successCount).isEqualTo(1);
        assertThat(failedCount).isEqualTo(1);

        Long bid510kCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bids WHERE auction_id = ? AND amount = 510000.00", Long.class, auction.getId()
        );
        assertThat(bid510kCount).isEqualTo(1L);
    }

    @RepeatedConcurrencyTest
    @DisplayName("INT-CON-02 10 người đặt giá cùng lúc: current_price cao nhất, đúng 1 LEADING, bid_count đúng")
    void testTenConcurrentBidsDifferentAmounts() throws Exception {
        LocalDateTime now = LocalDateTime.now(mutableClock);
        Product product = seed.createProduct(seller, category, "Silver Bar", ProductStatus.IN_AUCTION);
        Auction auction = seed.createAuction(seller, product, new BigDecimal("500000"), new BigDecimal("10000"),
                now.minusMinutes(10), now.plusMinutes(20), AuctionStatus.ACTIVE);

        // 10 luồng đặt các mức 510k..600k
        List<ResponseEntity<String>> results = Concurrently.runIndexed(10, (index) -> {
            User bidder = (index % 2 == 0) ? buyer : bidderB;
            long amount = 510000 + (index * 10000L);
            return api.as(bidder).post("/bids", Map.of("auctionId", auction.getId(), "amount", amount));
        });

        int successCount = 0;
        for (ResponseEntity<String> res : results) {
            if (res.getStatusCode().is2xxSuccessful()) {
                successCount++;
            }
        }
        assertThat(successCount).isGreaterThan(0);

        // Kiểm tra đúng 1 Bid LEADING
        Long leadingCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bids WHERE auction_id = ? AND status = 'LEADING'", Long.class, auction.getId()
        );
        assertThat(leadingCount).isEqualTo(1L);

        // current_price trong auctions khớp với LEADING bid
        BigDecimal highestSuccessfulBid = jdbcTemplate.queryForObject(
                "SELECT amount FROM bids WHERE auction_id = ? AND status = 'LEADING'", BigDecimal.class, auction.getId()
        );
        BigDecimal currentPrice = jdbcTemplate.queryForObject(
                "SELECT current_price FROM auctions WHERE id = ?", BigDecimal.class, auction.getId()
        );
        assertThat(currentPrice).isEqualByComparingTo(highestSuccessfulBid);

        // bid_count khớp số lượng valid bids
        Integer bidCount = jdbcTemplate.queryForObject(
                "SELECT bid_count FROM auctions WHERE id = ?", Integer.class, auction.getId()
        );
        assertThat(bidCount).isEqualTo(successCount);
    }

    @RepeatedConcurrencyTest
    @DisplayName("INT-CON-03 Đặt giá đúng lúc chốt (R-02): không có Bid ghi sau closed_at")
    void testBidAtExactClosingTime() throws Exception {
        LocalDateTime now = LocalDateTime.now(mutableClock);
        Product product = seed.createProduct(seller, category, "Vintage Camera", ProductStatus.IN_AUCTION);
        Auction auction = seed.createAuction(seller, product, new BigDecimal("500000"), new BigDecimal("10000"),
                now.minusMinutes(10), now.plusSeconds(1), AuctionStatus.ACTIVE);

        // Luồng A: đặt giá, Luồng B: advance thời gian và chốt phiên
        List<Object> results = Concurrently.runIndexed(2, (index) -> {
            if (index == 0) {
                return api.as(buyer).post("/bids", Map.of("auctionId", auction.getId(), "amount", 510000));
            } else {
                mutableClock.advance(Duration.ofSeconds(2));
                auctionScheduler.scheduleAuctionStatusTransitions();
                return "CLOSED";
            }
        });

        // Không có Bid nào created_at >= closed_at
        List<Map<String, Object>> bids = jdbcTemplate.queryForList(
                "SELECT b.created_at as bid_time, a.closed_at as close_time, b.status " +
                "FROM bids b JOIN auctions a ON a.id = b.auction_id WHERE a.id = ?", auction.getId()
        );

        for (Map<String, Object> b : bids) {
            java.sql.Timestamp bidTime = (java.sql.Timestamp) b.get("bid_time");
            java.sql.Timestamp closeTime = (java.sql.Timestamp) b.get("close_time");
            if (closeTime != null) {
                assertThat(bidTime.toInstant()).isBeforeOrEqualTo(closeTime.toInstant());
            }
        }
    }

    @RepeatedConcurrencyTest
    @DisplayName("INT-CON-04 Hai tiến trình chốt cùng phiên (R-03): đúng 1 Order")
    void testTwoProcessesClosingSameAuction() throws Exception {
        LocalDateTime now = LocalDateTime.now(mutableClock);
        Product product = seed.createProduct(seller, category, "Painting", ProductStatus.IN_AUCTION);
        Auction auction = seed.createAuction(seller, product, new BigDecimal("500000"), new BigDecimal("10000"),
                now.minusMinutes(20), now.plusMinutes(5), AuctionStatus.ACTIVE);

        // Seed 1 bid hợp lệ
        api.as(buyer).post("/bids", Map.of("auctionId", auction.getId(), "amount", 510000));

        // Hết giờ
        mutableClock.advance(Duration.ofMinutes(10));

        // 2 luồng cùng gọi chốt phiên
        Concurrently.run(2, () -> {
            auctionScheduler.scheduleAuctionStatusTransitions();
            return null;
        });

        Long orderCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE auction_id = ?", Long.class, auction.getId()
        );
        assertThat(orderCount).isEqualTo(1L);
    }

    @RepeatedConcurrencyTest
    @DisplayName("INT-CON-05 Thanh toán đúng lúc hết hạn (R-04): Payment tạo trước hạn -> Order PAID, không EXPIRED")
    void testPayAtExactExpirationTime() throws Exception {
        Order order = seed.orderPendingPayment(buyer, seller);
        // Đặt clock = deadline - 1s theo đặc tả R-04
        mutableClock.set(order.getPaymentDeadline().minusSeconds(1).atZone(java.time.ZoneId.systemDefault()).toInstant());

        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        gateway.setLatch(latch);

        String key = "KEY-EXP-" + UUID.randomUUID();

        Concurrently.runIndexed(2, (index) -> {
            if (index == 0) {
                return api.as(buyer).post(
                        "/orders/" + order.getId() + "/payments",
                        Map.of("method", "MOCK_CARD", "simulateResult", "SUCCESS"),
                        Map.of("Idempotency-Key", key)
                );
            } else {
                // Đợi thanh toán vào gateway
                org.awaitility.Awaitility.await().atMost(5, java.util.concurrent.TimeUnit.SECONDS).until(() ->
                        gateway.chargeCount() > 0
                );
                mutableClock.advance(Duration.ofSeconds(2)); // Vượt payment_deadline đúng 1s theo đặc tả
                orderScheduler.tick(); // Phải skip vì có Payment PENDING
                latch.countDown(); // Nhả gateway để hoàn tất
                return null;
            }
        });

        String orderStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM orders WHERE id = ?", String.class, order.getId()
        );
        assertThat(orderStatus).isEqualTo("PAID");
    }

    @RepeatedConcurrencyTest
    @DisplayName("INT-CON-06 Thanh toán vs Admin hủy đơn (R-05): Order CANCELLED, Payment FAILED (ORDER_CANCELLED)")
    void testPayVsAdminCancel() throws Exception {
        Order order = seed.orderPendingPayment(buyer, seller);
        java.util.concurrent.CountDownLatch inGateway = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        gateway.setLatch(latch);

        String key = "KEY-CANCEL-" + UUID.randomUUID();

        List<Object> results = Concurrently.runIndexed(2, (index) -> {
            if (index == 0) {
                return api.as(buyer).post(
                        "/orders/" + order.getId() + "/payments",
                        Map.of("method", "MOCK_CARD", "simulateResult", "SUCCESS"),
                        Map.of("Idempotency-Key", key)
                );
            } else {
                // Đợi thanh toán vào gateway
                org.awaitility.Awaitility.await().atMost(5, java.util.concurrent.TimeUnit.SECONDS).until(() ->
                        gateway.chargeCount() > 0
                );
                ResponseEntity<String> cancelRes = api.as(admin).post(
                        "/admin/orders/" + order.getId() + "/cancel",
                        Map.of("reason", "Hủy đơn hàng khẩn cấp")
                );
                System.out.println("CANCEL RES: " + cancelRes.getStatusCode() + " body: " + cancelRes.getBody());
                latch.countDown();
                return cancelRes;
            }
        });

        String orderStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM orders WHERE id = ?", String.class, order.getId()
        );
        assertThat(orderStatus).isEqualTo("CANCELLED");

        String paymentStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM payments WHERE order_id = ? AND idempotency_key = ?",
                String.class, order.getId(), key
        );
        assertThat(paymentStatus).isEqualTo("FAILED");

        String failReason = jdbcTemplate.queryForObject(
                "SELECT failure_reason FROM payments WHERE order_id = ? AND idempotency_key = ?",
                String.class, order.getId(), key
        );
        assertThat(failReason).contains("ORDER_CANCELLED");

        // Không có shipment
        Long shipmentCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shipments WHERE order_id = ?", Long.class, order.getId()
        );
        assertThat(shipmentCount).isEqualTo(0L);
    }

    @RepeatedConcurrencyTest
    @DisplayName("INT-CON-07 Xác nhận vs khiếu nại (R-06): Đúng một trong hai thành công, bên còn lại 409")
    void testConfirmReceiptVsComplaint() throws Exception {
        Order order = seed.orderDelivered(buyer, seller);

        List<ResponseEntity<String>> results = Concurrently.runIndexed(2, (index) -> {
            if (index == 0) {
                return api.as(buyer).patch("/orders/" + order.getId() + "/confirm-receipt", Map.of());
            } else {
                return api.as(buyer).post("/reports", Map.of(
                        "reportType", "COMPLAINT",
                        "orderId", order.getId(),
                        "reason", "Hang bi vo man hinh khi nhan"
                ));
            }
        });

        ResponseEntity<String> confirmRes = results.get(0);
        ResponseEntity<String> reportRes = results.get(1);

        if (confirmRes.getStatusCode().is2xxSuccessful()) {
            assertThat(reportRes.getStatusCode().value()).isEqualTo(409);
            assertThat(reportRes.getBody()).contains("COMPLAINT_WINDOW_CLOSED");
            String status = jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, order.getId());
            assertThat(status).isEqualTo("COMPLETED");
        } else {
            assertThat(reportRes.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(confirmRes.getStatusCode().value()).isEqualTo(409);
            assertThat(confirmRes.getBody()).contains("COMPLAINT_PENDING");
            String status = jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, order.getId());
            assertThat(status).isEqualTo("SHIPPING");
        }
    }

    @RepeatedConcurrencyTest
    @DisplayName("INT-CON-08 Khóa user vs đặt giá của chính user (R-07): không còn Bid hợp lệ của OTHER")
    void testBanUserVsOwnBid() throws Exception {
        LocalDateTime now = LocalDateTime.now(mutableClock);
        Product product = seed.createProduct(seller, category, "Rare Stamp", ProductStatus.IN_AUCTION);
        Auction auction = seed.createAuction(seller, product, new BigDecimal("100000"), new BigDecimal("10000"),
                now.minusMinutes(10), now.plusMinutes(20), AuctionStatus.ACTIVE);

        List<ResponseEntity<String>> results = Concurrently.runIndexed(2, (index) -> {
            if (index == 0) {
                return api.as(other).post("/bids", Map.of("auctionId", auction.getId(), "amount", 110000));
            } else {
                return api.as(admin).patch(
                        "/admin/users/" + other.getId(),
                        Map.of("status", "BANNED", "reason", "Khoa tai khoan test dong thoi")
                );
            }
        });

        // Không còn Bid LEADING của OTHER
        Long otherLeadingBids = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bids WHERE bidder_id = ? AND auction_id = ? AND status = 'LEADING'",
                Long.class, other.getId(), auction.getId()
        );
        assertThat(otherLeadingBids).isEqualTo(0L);

        // Khôi phục OTHER để bảo vệ invariant
        jdbcTemplate.update("UPDATE users SET status = 'ACTIVE' WHERE id = ?", other.getId());
    }

    @RepeatedConcurrencyTest
    @DisplayName("INT-CON-09 Hai cập nhật Shipment cùng bước (R-08): 1 thành công 200, 1 bị 409, đúng 1 event PICKED_UP")
    void testTwoShipmentStatusUpdatesSameStep() throws Exception {
        Order order = seed.orderPaid(buyer, seller);
        Long shipmentId = jdbcTemplate.queryForObject(
                "SELECT id FROM shipments WHERE order_id = ?", Long.class, order.getId()
        );

        List<ResponseEntity<String>> results = Concurrently.runIndexed(2, (index) ->
                api.as(seller).patch("/shipments/" + shipmentId + "/status", Map.of("status", "PICKED_UP"))
        );

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

        Long pickedUpEvents = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shipment_events WHERE shipment_id = ? AND status = 'PICKED_UP'",
                Long.class, shipmentId
        );
        assertThat(pickedUpEvents).isEqualTo(1L);
    }
}
