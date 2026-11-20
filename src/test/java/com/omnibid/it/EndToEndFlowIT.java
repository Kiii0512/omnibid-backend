package com.omnibid.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnibid.entity.Auction;
import com.omnibid.entity.Order;
import com.omnibid.entity.Product;
import com.omnibid.enums.AuctionStatus;
import com.omnibid.enums.OrderStatus;
import com.omnibid.enums.ProductStatus;
import com.omnibid.support.BaseIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EndToEndFlowIT extends BaseIT {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("INT-E2E-01 Luồng chính từ đặt giá đến hoàn tất đơn hàng")
    void testEndToEndMainFlow() throws Exception {
        Product product = seed.createProduct(seller, category, "iPhone 15 Pro", ProductStatus.AVAILABLE);

        LocalDateTime now = LocalDateTime.now(mutableClock);
        Auction auction = seed.createAuction(
                seller, product,
                new BigDecimal("500000"),
                new BigDecimal("10000"),
                now,
                now.plusHours(1),
                AuctionStatus.ACTIVE
        );
        product.setStatus(ProductStatus.IN_AUCTION);
        jdbcTemplate.update("UPDATE products SET status = 'IN_AUCTION' WHERE id = ?", product.getId());

        // 2) BUYER và BIDDER_B đặt giá xen kẽ qua API
        ResponseEntity<String> bid1 = api.as(buyer).post(
                "/bids",
                Map.of("auctionId", auction.getId(), "amount", 510000)
        );
        assertThat(bid1.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<String> bid2 = api.as(bidderB).post(
                "/bids",
                Map.of("auctionId", auction.getId(), "amount", 520000)
        );
        assertThat(bid2.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<String> bid3 = api.as(buyer).post(
                "/bids",
                Map.of("auctionId", auction.getId(), "amount", 530000)
        );
        assertThat(bid3.getStatusCode().is2xxSuccessful()).isTrue();

        // 3) Clock advance 1h và chốt phiên
        mutableClock.advance(Duration.ofHours(1).plusSeconds(5));
        auctionScheduler.scheduleAuctionStatusTransitions();

        String auctionStatus = jdbcTemplate.queryForObject("SELECT status FROM auctions WHERE id = ?", String.class, auction.getId());
        assertThat(auctionStatus).isEqualTo("ENDED");

        Long orderId = jdbcTemplate.queryForObject("SELECT id FROM orders WHERE auction_id = ?", Long.class, auction.getId());
        assertThat(orderId).isNotNull();

        String orderStatus = jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
        assertThat(orderStatus).isEqualTo("PENDING_PAYMENT");

        String productStatus = jdbcTemplate.queryForObject("SELECT status FROM products WHERE id = ?", String.class, product.getId());
        assertThat(productStatus).isEqualTo("RESERVED");

        // 4) BUYER GET /orders
        ResponseEntity<String> getOrders = api.as(buyer).get("/orders");
        assertThat(getOrders.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(getOrders.getBody()).contains("\"id\":" + orderId);

        // 5) BUYER PATCH shipping-info
        ResponseEntity<String> patchShipping = api.as(buyer).patch(
                "/orders/" + orderId + "/shipping-info",
                Map.of("shippingAddress", "456 Le Loi, Da Nang", "phoneNumber", "0912345678")
        );
        assertThat(patchShipping.getStatusCode().is2xxSuccessful()).isTrue();

        // 6) BUYER POST payments simulateResult=FAILED
        String failKey = "KEY-FAIL-" + UUID.randomUUID();
        ResponseEntity<String> payFailed = api.as(buyer).post(
                "/orders/" + orderId + "/payments",
                Map.of("method", "MOCK_CARD", "simulateResult", "FAILED"),
                Map.of("Idempotency-Key", failKey)
        );
        assertThat(payFailed.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(payFailed.getBody()).contains("FAILED");

        // 7) BUYER POST payments key mới simulateResult=SUCCESS
        String successKey = "KEY-SUCCESS-" + UUID.randomUUID();
        ResponseEntity<String> paySuccess = api.as(buyer).post(
                "/orders/" + orderId + "/payments",
                Map.of("method", "MOCK_CARD", "simulateResult", "SUCCESS"),
                Map.of("Idempotency-Key", successKey)
        );
        assertThat(paySuccess.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(paySuccess.getBody()).contains("SUCCESS");

        orderStatus = jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
        assertThat(orderStatus).isEqualTo("PAID");

        Long shipmentId = jdbcTemplate.queryForObject("SELECT id FROM shipments WHERE order_id = ?", Long.class, orderId);
        assertThat(shipmentId).isNotNull();

        // 8) SELLER cập nhật status shipment: PICKED_UP -> IN_TRANSIT -> DELIVERED
        ResponseEntity<String> pickUpRes = api.as(seller).patch(
                "/shipments/" + shipmentId + "/status",
                Map.of("status", "PICKED_UP", "note", "Picked up by courier")
        );
        assertThat(pickUpRes.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<String> transitRes = api.as(seller).patch(
                "/shipments/" + shipmentId + "/status",
                Map.of("status", "IN_TRANSIT", "note", "On delivery route")
        );
        assertThat(transitRes.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<String> deliveredRes = api.as(seller).patch(
                "/shipments/" + shipmentId + "/status",
                Map.of("status", "DELIVERED", "note", "Delivered to buyer")
        );
        assertThat(deliveredRes.getStatusCode().is2xxSuccessful()).isTrue();

        orderStatus = jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
        assertThat(orderStatus).isEqualTo("SHIPPING");

        // 9) BUYER confirm-receipt
        ResponseEntity<String> confirmRes = api.as(buyer).patch(
                "/orders/" + orderId + "/confirm-receipt",
                Map.of()
        );
        assertThat(confirmRes.getStatusCode().is2xxSuccessful()).isTrue();

        orderStatus = jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
        assertThat(orderStatus).isEqualTo("COMPLETED");

        productStatus = jdbcTemplate.queryForObject("SELECT status FROM products WHERE id = ?", String.class, product.getId());
        assertThat(productStatus).isEqualTo("SOLD");

        // 2 lần thử thanh toán trong payments
        Integer paymentAttempts = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payments WHERE order_id = ?", Integer.class, orderId);
        assertThat(paymentAttempts).isEqualTo(2);
    }

    @Test
    @DisplayName("INT-E2E-02 Hết hạn thanh toán chuyển Order sang EXPIRED, Product AVAILABLE")
    void testOrderPaymentExpired() {
        Order order = seed.orderPendingPayment(buyer, seller);

        // Tiến thời gian 48 giờ
        mutableClock.advance(Duration.ofHours(48).plusSeconds(10));
        orderScheduler.tick();

        String orderStatus = jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, order.getId());
        assertThat(orderStatus).isEqualTo("EXPIRED");

        String productStatus = jdbcTemplate.queryForObject("SELECT status FROM products WHERE id = ?", String.class, order.getProductId());
        assertThat(productStatus).isEqualTo("AVAILABLE");

        // SELLER tạo phiên mới cho cùng Product thành công
        LocalDateTime now = LocalDateTime.now(mutableClock);
        Auction newAuction = seed.createAuction(
                seller,
                jdbcTemplate.queryForObject("SELECT id, title FROM products WHERE id = ?",
                        (rs, rowNum) -> Product.builder().id(rs.getLong("id")).title(rs.getString("title")).build(),
                        order.getProductId()),
                new BigDecimal("500000"),
                new BigDecimal("10000"),
                now,
                now.plusHours(2),
                AuctionStatus.ACTIVE
        );
        assertThat(newAuction.getId()).isNotNull();
        jdbcTemplate.update("UPDATE products SET status = 'IN_AUCTION' WHERE id = ?", order.getProductId());

        // BUYER cố gắng thanh toán đơn đã EXPIRED -> 409 ORDER_EXPIRED
        ResponseEntity<String> payRes = api.as(buyer).post(
                "/orders/" + order.getId() + "/payments",
                Map.of("method", "MOCK_CARD", "simulateResult", "SUCCESS"),
                Map.of("Idempotency-Key", "KEY-" + UUID.randomUUID())
        );
        assertThat(payRes.getStatusCode().value()).isEqualTo(409);
        assertThat(payRes.getBody()).contains("ORDER_EXPIRED");

        // Cập nhật trạng thái auction và product để giữ invariant sạch
        jdbcTemplate.update("UPDATE auctions SET status = 'CANCELLED' WHERE id = ?", newAuction.getId());
        jdbcTemplate.update("UPDATE products SET status = 'AVAILABLE' WHERE id = ?", order.getProductId());
    }

    @Test
    @DisplayName("INT-E2E-03 Đơn hàng tự hoàn tất sau 7 ngày DELIVERED")
    void testOrderAutoCompleteAfterDelivered() {
        Order order = seed.orderDelivered(buyer, seller);

        // Tiến thời gian 7 ngày
        mutableClock.advance(Duration.ofDays(7).plusSeconds(10));
        orderScheduler.tick();

        String orderStatus = jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, order.getId());
        assertThat(orderStatus).isEqualTo("COMPLETED");

        String productStatus = jdbcTemplate.queryForObject("SELECT status FROM products WHERE id = ?", String.class, order.getProductId());
        assertThat(productStatus).isEqualTo("SOLD");
    }

    @Test
    @DisplayName("INT-E2E-04 Khiếu nại tạm dừng tự hoàn tất, Admin xử lý hủy đơn")
    void testComplaintPausesAutoComplete() {
        Order order = seed.orderDelivered(buyer, seller);

        // BUYER gửi khiếu nại COMPLAINT
        ResponseEntity<String> complaintRes = api.as(buyer).post(
                "/reports",
                Map.of(
                        "reportType", "COMPLAINT",
                        "orderId", order.getId(),
                        "reason", "Hang bi vo man hinh khi nhan"
                )
        );
        assertThat(complaintRes.getStatusCode().is2xxSuccessful()).isTrue();

        Long reportId = jdbcTemplate.queryForObject("SELECT id FROM reports WHERE order_id = ?", Long.class, order.getId());
        assertThat(reportId).isNotNull();

        // Tiến thời gian 7 ngày và tick()
        mutableClock.advance(Duration.ofDays(7).plusSeconds(10));
        orderScheduler.tick();

        // Order vẫn SHIPPING vì có complaint PENDING
        String orderStatus = jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, order.getId());
        assertThat(orderStatus).isEqualTo("SHIPPING");

        // BUYER confirm-receipt trong lúc complaint PENDING -> 409 COMPLAINT_PENDING
        ResponseEntity<String> confirmRes = api.as(buyer).patch(
                "/orders/" + order.getId() + "/confirm-receipt",
                Map.of()
        );
        assertThat(confirmRes.getStatusCode().value()).isEqualTo(409);
        assertThat(confirmRes.getBody()).contains("COMPLAINT_PENDING");

        // Admin xử lý giải quyết CANCEL_ORDER
        ResponseEntity<String> resolveRes = api.as(admin).patch(
                "/admin/reports/" + reportId + "/resolve",
                Map.of("action", "CANCEL_ORDER", "note", "Chấp nhận khiếu nại của buyer")
        );
        assertThat(resolveRes.getStatusCode().is2xxSuccessful()).isTrue();

        orderStatus = jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, order.getId());
        assertThat(orderStatus).isEqualTo("CANCELLED");

        String productStatus = jdbcTemplate.queryForObject("SELECT status FROM products WHERE id = ?", String.class, order.getProductId());
        assertThat(productStatus).isEqualTo("AVAILABLE");

        String reportStatus = jdbcTemplate.queryForObject("SELECT status FROM reports WHERE id = ?", String.class, reportId);
        assertThat(reportStatus).isEqualTo("RESOLVED");
    }

    @Test
    @DisplayName("INT-E2E-05 Phiên không đạt giá sàn chuyển UNSOLD, không tạo Order")
    void testAuctionReserveNotMet() {
        Product product = seed.createProduct(seller, category, "Rare Watch", ProductStatus.AVAILABLE);

        LocalDateTime now = LocalDateTime.now(mutableClock);
        Auction auction = seed.createAuction(
                seller, product,
                new BigDecimal("500000"),
                new BigDecimal("10000"),
                now,
                now.plusHours(1),
                AuctionStatus.ACTIVE
        );
        auction.setReservePrice(new BigDecimal("600000"));
        jdbcTemplate.update("UPDATE auctions SET reserve_price = 600000 WHERE id = ?", auction.getId());

        // Bid cao nhất chỉ 540k
        api.as(buyer).post("/bids", Map.of("auctionId", auction.getId(), "amount", 540000));

        // Hết giờ
        mutableClock.advance(Duration.ofHours(1).plusSeconds(5));
        auctionScheduler.scheduleAuctionStatusTransitions();

        String auctionStatus = jdbcTemplate.queryForObject("SELECT status FROM auctions WHERE id = ?", String.class, auction.getId());
        assertThat(auctionStatus).isEqualTo("UNSOLD");

        Integer orderCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders WHERE auction_id = ?", Integer.class, auction.getId());
        assertThat(orderCount).isEqualTo(0);

        String productStatus = jdbcTemplate.queryForObject("SELECT status FROM products WHERE id = ?", String.class, product.getId());
        assertThat(productStatus).isEqualTo("AVAILABLE");
    }

    @Test
    @DisplayName("INT-E2E-06 Nhắc thanh toán đúng 2 mốc 24h và 2h")
    void testPaymentReminders() {
        Order order = seed.orderPendingPayment(buyer, seller);

        // Mốc 24h
        mutableClock.advance(Duration.ofHours(24).plusMinutes(1));
        orderScheduler.tick();
        orderScheduler.tick(); // gọi lại không tạo thêm

        Integer reminderCount1 = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND type = 'PAYMENT_REMINDER'",
                Integer.class, buyer.getId()
        );
        assertThat(reminderCount1).isEqualTo(1);

        // Mốc 2h (tiến thêm 22h)
        mutableClock.advance(Duration.ofHours(22).plusMinutes(1));
        orderScheduler.tick();
        orderScheduler.tick();

        Integer reminderCount2 = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND type = 'PAYMENT_REMINDER'",
                Integer.class, buyer.getId()
        );
        assertThat(reminderCount2).isEqualTo(2);
    }
}
