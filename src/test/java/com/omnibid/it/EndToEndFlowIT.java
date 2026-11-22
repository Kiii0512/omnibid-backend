package com.omnibid.it;

import com.omnibid.entity.Auction;
import com.omnibid.entity.Order;
import com.omnibid.entity.Product;
import com.omnibid.enums.AuctionStatus;
import com.omnibid.enums.ProductStatus;
import com.omnibid.support.BaseIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EndToEndFlowIT extends BaseIT {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    @DisplayName("INT-E2E-01 Luồng chính v4: đặt giá -> chốt phiên -> thanh toán -> gửi hàng (+7d) -> xác nhận -> SOLD")
    void testEndToEndMainFlow() {
        Product product = seed.createProduct(seller, category, "iPhone 15 Pro", ProductStatus.IN_AUCTION);
        Instant now = mutableClock.instant();
        Auction auction = seed.createAuction(
                seller, product,
                new BigDecimal("500000"),
                new BigDecimal("10000"),
                now.minus(5, ChronoUnit.MINUTES),
                now.plus(1, ChronoUnit.HOURS),
                AuctionStatus.ACTIVE
        );

        assertThat(api.as(buyer).post("/auctions/" + auction.getId() + "/bids", Map.of("amount", 510000)).getStatusCode().value()).isEqualTo(201);
        assertThat(api.as(bidderB).post("/auctions/" + auction.getId() + "/bids", Map.of("amount", 520000)).getStatusCode().value()).isEqualTo(201);
        assertThat(api.as(buyer).post("/auctions/" + auction.getId() + "/bids", Map.of("amount", 530000)).getStatusCode().value()).isEqualTo(201);

        mutableClock.advance(Duration.ofHours(1).plusSeconds(5));
        auctionScheduler.scheduleAuctionStatusTransitions();

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM auctions WHERE id = ?", String.class, auction.getId())).isEqualTo("ENDED");
        Long orderId = jdbcTemplate.queryForObject("SELECT id FROM orders WHERE auction_id = ?", Long.class, auction.getId());
        assertThat(orderId).isNotNull();
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM products WHERE id = ?", String.class, product.getId())).isEqualTo("RESERVED");

        assertThat(api.as(buyer).patch("/orders/" + orderId + "/shipping-info",
                Map.of("shippingAddress", "456 Le Loi, Da Nang", "phoneNumber", "0912345678")).getStatusCode().value()).isEqualTo(200);

        ResponseEntity<String> payFailed = api.as(buyer).post(
                "/orders/" + orderId + "/payments",
                Map.of("method", "MOCK_CARD", "simulateResult", "FAILED"),
                Map.of("Idempotency-Key", UUID.randomUUID().toString())
        );
        assertThat(payFailed.getStatusCode().value()).isEqualTo(200);
        assertThat(payFailed.getBody()).contains("FAILED");

        ResponseEntity<String> paySuccess = api.as(buyer).post(
                "/orders/" + orderId + "/payments",
                Map.of("method", "MOCK_CARD", "simulateResult", "SUCCESS"),
                Map.of("Idempotency-Key", UUID.randomUUID().toString())
        );
        assertThat(paySuccess.getStatusCode().value()).isEqualTo(200);
        assertThat(paySuccess.getBody()).contains("SUCCESS");

        ResponseEntity<String> shipRes = api.as(seller).post(
                "/orders/" + orderId + "/ship",
                Map.of("trackingCode", "VN123456789")
        );
        assertThat(shipRes.getStatusCode().value()).isEqualTo(200);
        JsonNode shipData = jsonMapper.readTree(shipRes.getBody()).path("data");
        Instant shippedAt = OffsetDateTime.parse(shipData.path("shippedAt").asText()).toInstant();
        Instant confirmDeadline = OffsetDateTime.parse(shipData.path("confirmDeadline").asText()).toInstant();
        assertThat(Duration.between(shippedAt, confirmDeadline)).isEqualTo(Duration.ofDays(7));

        ResponseEntity<String> confirmRes = api.as(buyer).post("/orders/" + orderId + "/confirm-receipt", null);
        assertThat(confirmRes.getStatusCode().value()).isEqualTo(200);

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId)).isEqualTo("COMPLETED");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM products WHERE id = ?", String.class, product.getId())).isEqualTo("SOLD");
    }

    @Test
    @DisplayName("INT-E2E-02 Hết hạn thanh toán -> EXPIRED, Product AVAILABLE")
    void testOrderPaymentExpired() {
        Order order = seed.orderPendingPayment(buyer, seller);
        mutableClock.advance(Duration.ofHours(48).plusSeconds(10));
        orderScheduler.tick();

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, order.getId())).isEqualTo("EXPIRED");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM products WHERE id = ?", String.class, order.getProductId())).isEqualTo("AVAILABLE");

        ResponseEntity<String> payRes = api.as(buyer).post(
                "/orders/" + order.getId() + "/payments",
                Map.of("method", "MOCK_CARD", "simulateResult", "SUCCESS"),
                Map.of("Idempotency-Key", UUID.randomUUID().toString())
        );
        assertThat(payRes.getStatusCode().value()).isEqualTo(409);
        assertThat(payRes.getBody()).contains("ORDER_EXPIRED");
    }

    @Test
    @DisplayName("INT-E2E-03 Đơn hàng tự hoàn tất sau 7 ngày SHIPPING")
    void testOrderAutoCompleteAfterShipping() {
        Order order = seed.orderShipping(buyer, seller);
        mutableClock.advance(Duration.ofDays(7).plusSeconds(10));
        orderScheduler.tick();

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, order.getId())).isEqualTo("COMPLETED");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM products WHERE id = ?", String.class, order.getProductId())).isEqualTo("SOLD");
    }

    @Test
    @DisplayName("INT-E2E-04 Phiên không đạt giá sàn chuyển UNSOLD, không tạo Order")
    void testAuctionReserveNotMet() {
        Product product = seed.createProduct(seller, category, "Rare Watch", ProductStatus.IN_AUCTION);
        Instant now = mutableClock.instant();
        Auction auction = seed.createAuction(
                seller, product,
                new BigDecimal("500000"),
                new BigDecimal("10000"),
                now.minus(5, ChronoUnit.MINUTES),
                now.plus(1, ChronoUnit.HOURS),
                AuctionStatus.ACTIVE
        );
        jdbcTemplate.update("UPDATE auctions SET reserve_price = 600000 WHERE id = ?", auction.getId());

        api.as(buyer).post("/auctions/" + auction.getId() + "/bids", Map.of("amount", 540000));
        mutableClock.advance(Duration.ofHours(1).plusSeconds(5));
        auctionScheduler.scheduleAuctionStatusTransitions();

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM auctions WHERE id = ?", String.class, auction.getId())).isEqualTo("UNSOLD");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders WHERE auction_id = ?", Integer.class, auction.getId())).isEqualTo(0);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM products WHERE id = ?", String.class, product.getId())).isEqualTo("AVAILABLE");
    }
}
