package com.omnibid.it;

import com.omnibid.entity.Auction;
import com.omnibid.entity.Order;
import com.omnibid.entity.Product;
import com.omnibid.enums.AuctionStatus;
import com.omnibid.enums.ProductStatus;
import com.omnibid.support.BaseIT;
import com.omnibid.support.Concurrently;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class AdminIntegrationIT extends BaseIT {

    @Test
    @DisplayName("INT-ADM-01 BR-09 & BR-17: Khóa người dẫn đầu giữ nguyên bid; chốt phiên -> UNSOLD, không trao người thứ hai")
    void testBanLeadingBidderKeepsBidsAndClosesUnsold() {
        Instant now = mutableClock.instant();
        Product product = seed.createProduct(seller, category, "Antique Vase", ProductStatus.IN_AUCTION);
        Auction auction = seed.createAuction(seller, product, new BigDecimal("500000"), new BigDecimal("10000"),
                now.minus(30, ChronoUnit.MINUTES), now.plus(30, ChronoUnit.MINUTES), AuctionStatus.ACTIVE);

        api.as(other).post("/auctions/" + auction.getId() + "/bids", Map.of("amount", 520000));
        api.as(bidderB).post("/auctions/" + auction.getId() + "/bids", Map.of("amount", 530000));
        api.as(other).post("/auctions/" + auction.getId() + "/bids", Map.of("amount", 540000));

        ResponseEntity<String> banRes = api.as(admin).post(
                "/admin/users/" + other.getId() + "/ban",
                Map.of("reason", "Vi phạm quy chế đấu giá")
        );
        assertThat(banRes.getStatusCode().value()).isEqualTo(200);

        // Giữ nguyên bid và người dẫn đầu trong khi phiên chạy (BR-03, BR-17d)
        Map<String, Object> aucData = jdbcTemplate.queryForMap(
                "SELECT current_price, highest_bidder_id, bid_count FROM auctions WHERE id = ?", auction.getId()
        );
        assertThat(new BigDecimal(aucData.get("current_price").toString())).isEqualByComparingTo("540000");
        assertThat(((Number) aucData.get("highest_bidder_id")).longValue()).isEqualTo(other.getId());
        assertThat(((Number) aucData.get("bid_count")).intValue()).isEqualTo(3);

        // Hết giờ -> chốt phiên -> leader BANNED -> UNSOLD, không tạo Order (BR-09)
        mutableClock.advance(Duration.ofMinutes(35));
        auctionScheduler.scheduleAuctionStatusTransitions();

        String status = jdbcTemplate.queryForObject("SELECT status FROM auctions WHERE id = ?", String.class, auction.getId());
        assertThat(status).isEqualTo("UNSOLD");
        Long orderCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders WHERE auction_id = ?", Long.class, auction.getId());
        assertThat(orderCount).isEqualTo(0L);
    }

    @Test
    @DisplayName("INT-ADM-02 Khóa ADMIN -> 409 CANNOT_BAN_ADMIN")
    void testCannotBanAdmin() {
        ResponseEntity<String> res = api.as(admin).post("/admin/users/" + admin.getId() + "/ban", Map.of("reason", "test"));
        assertThat(res.getStatusCode().value()).isEqualTo(409);
        assertThat(res.getBody()).contains("CANNOT_BAN_ADMIN");
    }

    @Test
    @DisplayName("INT-ADM-03 Khóa seller: hủy phiên đang bán và đơn PENDING_PAYMENT, giữ nguyên đơn SHIPPING")
    void testBanSellerWithMixedData() {
        Instant now = mutableClock.instant();
        Product prod1 = seed.createProduct(seller, category, "Prod 1", ProductStatus.IN_AUCTION);
        Auction auc1 = seed.createAuction(seller, prod1, new BigDecimal("100000"), new BigDecimal("10000"),
                now.plus(1, ChronoUnit.HOURS), now.plus(2, ChronoUnit.HOURS), AuctionStatus.SCHEDULED);

        Product prod2 = seed.createProduct(seller, category, "Prod 2", ProductStatus.IN_AUCTION);
        Auction auc2 = seed.createAuction(seller, prod2, new BigDecimal("100000"), new BigDecimal("10000"),
                now.minus(30, ChronoUnit.MINUTES), now.plus(30, ChronoUnit.MINUTES), AuctionStatus.ACTIVE);
        api.as(bidderB).post("/auctions/" + auc2.getId() + "/bids", Map.of("amount", 110000));

        Order orderPending = seed.orderPendingPayment(buyer, seller);
        Order orderShipping = seed.orderShipping(buyer, seller);

        ResponseEntity<String> banRes = api.as(admin).post(
                "/admin/users/" + seller.getId() + "/ban",
                Map.of("reason", "Lừa đảo")
        );
        assertThat(banRes.getStatusCode().value()).isEqualTo(200);

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM auctions WHERE id = ?", String.class, auc1.getId())).isEqualTo("CANCELLED");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM auctions WHERE id = ?", String.class, auc2.getId())).isEqualTo("CANCELLED");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderPending.getId())).isEqualTo("CANCELLED");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderShipping.getId())).isEqualTo("SHIPPING");

        // Buyer xác nhận nhận hàng vẫn hoàn tất thành công
        ResponseEntity<String> confirmRes = api.as(buyer).post("/orders/" + orderShipping.getId() + "/confirm-receipt", null);
        assertThat(confirmRes.getStatusCode().value()).isEqualTo(200);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderShipping.getId())).isEqualTo("COMPLETED");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM products WHERE id = ?", String.class, orderShipping.getProductId())).isEqualTo("SOLD");
    }

    @Test
    @DisplayName("INT-ADM-04 BR-16 & BR-18: Ẩn sản phẩm khi SHIPPING -> khi COMPLETED sản phẩm vẫn HIDDEN")
    void testHideProductDuringShippingStaysHiddenOnComplete() {
        Order orderShipping = seed.orderShipping(buyer, seller);

        ResponseEntity<String> hideRes = api.as(admin).post(
                "/admin/products/" + orderShipping.getProductId() + "/hide",
                Map.of("reason", "Hàng vi phạm")
        );
        assertThat(hideRes.getStatusCode().value()).isEqualTo(200);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM products WHERE id = ?", String.class, orderShipping.getProductId()))
                .isEqualTo("HIDDEN");

        // Ẩn lần 2 -> 409 INVALID_STATUS_TRANSITION
        ResponseEntity<String> hideAgain = api.as(admin).post(
                "/admin/products/" + orderShipping.getProductId() + "/hide",
                Map.of("reason", "Lần 2")
        );
        assertThat(hideAgain.getStatusCode().value()).isEqualTo(409);

        // Buyer xác nhận nhận hàng -> Order COMPLETED nhưng Product vẫn HIDDEN (BR-16)
        ResponseEntity<String> confirmRes = api.as(buyer).post("/orders/" + orderShipping.getId() + "/confirm-receipt", null);
        assertThat(confirmRes.getStatusCode().value()).isEqualTo(200);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderShipping.getId()))
                .isEqualTo("COMPLETED");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM products WHERE id = ?", String.class, orderShipping.getProductId()))
                .isEqualTo("HIDDEN");
    }

    @Test
    @DisplayName("INT-ADM-05 Báo cáo: thiếu note -> 400; xử lý 2 lần -> 409; xử lý không đổi trạng thái user/product")
    void testReportResolveValidationAndIdempotency() throws Exception {
        Product product = seed.createProduct(seller, category, "Reported Item", ProductStatus.AVAILABLE);

        ResponseEntity<String> createRes = api.as(other).post(
                "/reports",
                Map.of("reportedProductId", product.getId(), "reason", "Nghi vấn hàng giả")
        );
        assertThat(createRes.getStatusCode().value()).isEqualTo(201);
        Long reportId = jdbcTemplate.queryForObject("SELECT id FROM reports WHERE reported_product_id = ?", Long.class, product.getId());

        // Thiếu note -> 400
        ResponseEntity<String> noNote = api.as(admin).post(
                "/admin/reports/" + reportId + "/resolve",
                Map.of("decision", "RESOLVED", "note", "")
        );
        assertThat(noNote.getStatusCode().value()).isEqualTo(400);

        // 2 admin xử lý đồng thời -> 1 thành công 200, 1 bị 409
        List<ResponseEntity<String>> results = Concurrently.runIndexed(2, idx -> api.as(admin).post(
                "/admin/reports/" + reportId + "/resolve",
                Map.of("decision", idx == 0 ? "RESOLVED" : "REJECTED", "note", "Đã xem xét " + idx)
        ));
        long okCount = results.stream().filter(r -> r.getStatusCode().value() == 200).count();
        long conflictCount = results.stream().filter(r -> r.getStatusCode().value() == 409).count();
        assertThat(okCount).isEqualTo(1L);
        assertThat(conflictCount).isEqualTo(1L);

        // Không tự động đổi trạng thái product
        String prodStatus = jdbcTemplate.queryForObject("SELECT status FROM products WHERE id = ?", String.class, product.getId());
        assertThat(prodStatus).isEqualTo("AVAILABLE");
    }

    @Test
    @DisplayName("INT-ADM-06 Thống kê tích lũy khớp DB")
    void testAdminStatisticsMatchDatabase() {
        seed.orderPaid(buyer, seller);
        ResponseEntity<String> statsRes = api.as(admin).get("/admin/statistics");
        assertThat(statsRes.getStatusCode().value()).isEqualTo(200);

        Long totalUsers = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Long.class);
        assertThat(statsRes.getBody()).contains("\"totalUsers\":" + totalUsers);
    }
}
