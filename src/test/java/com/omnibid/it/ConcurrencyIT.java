package com.omnibid.it;

import com.omnibid.entity.Auction;
import com.omnibid.entity.Product;
import com.omnibid.entity.User;
import com.omnibid.enums.AuctionStatus;
import com.omnibid.enums.ProductStatus;
import com.omnibid.support.BaseIT;
import com.omnibid.support.Concurrently;
import com.omnibid.support.RepeatedConcurrencyTest;
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

public class ConcurrencyIT extends BaseIT {

    @RepeatedConcurrencyTest
    @DisplayName("INT-CON-01 Hai người đặt cùng mức: 1 thành công, 1 lỗi BID_TOO_LOW kèm minNextBid")
    void testTwoBiddersSameAmount() throws Exception {
        Instant now = mutableClock.instant();
        Product product = seed.createProduct(seller, category, "Gold Coin", ProductStatus.IN_AUCTION);
        Auction auction = seed.createAuction(seller, product, new BigDecimal("500000"), new BigDecimal("10000"),
                now.minus(10, ChronoUnit.MINUTES), now.plus(20, ChronoUnit.MINUTES), AuctionStatus.ACTIVE);

        List<ResponseEntity<String>> results = Concurrently.runIndexed(2, index -> {
            User bidder = index == 0 ? buyer : bidderB;
            return api.as(bidder).post("/auctions/" + auction.getId() + "/bids", Map.of("amount", 510000));
        });

        int successCount = 0;
        int failedCount = 0;
        for (ResponseEntity<String> res : results) {
            if (res.getStatusCode().value() == 201) {
                successCount++;
            } else if (res.getStatusCode().value() == 409) {
                failedCount++;
                assertThat(res.getBody()).contains("BID_TOO_LOW");
                assertThat(res.getBody()).contains("520000");
            }
        }

        assertThat(successCount).isEqualTo(1);
        assertThat(failedCount).isEqualTo(1);
    }

    @Test
    @DisplayName("INT-CON-02 20 luồng đặt giá đồng thời: bất biến bid_count và current_price = MAX(amount)")
    void testTwentyConcurrentBids() throws Exception {
        Instant now = mutableClock.instant();
        Product product = seed.createProduct(seller, category, "Silver Bar", ProductStatus.IN_AUCTION);
        Auction auction = seed.createAuction(seller, product, new BigDecimal("500000"), new BigDecimal("10000"),
                now.minus(10, ChronoUnit.MINUTES), now.plus(20, ChronoUnit.MINUTES), AuctionStatus.ACTIVE);

        List<ResponseEntity<String>> results = Concurrently.runIndexed(20, index -> {
            User bidder = (index % 2 == 0) ? buyer : bidderB;
            long amount = 510000 + (index * 10000L);
            return api.as(bidder).post("/auctions/" + auction.getId() + "/bids", Map.of("amount", amount));
        });

        long successCount = results.stream().filter(r -> r.getStatusCode().value() == 201).count();
        assertThat(successCount).isGreaterThan(0L);

        Integer bidCount = jdbcTemplate.queryForObject(
                "SELECT bid_count FROM auctions WHERE id = ?", Integer.class, auction.getId()
        );
        Long actualBids = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bids WHERE auction_id = ?", Long.class, auction.getId()
        );
        BigDecimal maxBid = jdbcTemplate.queryForObject(
                "SELECT MAX(amount) FROM bids WHERE auction_id = ?", BigDecimal.class, auction.getId()
        );
        BigDecimal currentPrice = jdbcTemplate.queryForObject(
                "SELECT current_price FROM auctions WHERE id = ?", BigDecimal.class, auction.getId()
        );

        assertThat(bidCount.longValue()).isEqualTo(actualBids).isEqualTo(successCount);
        assertThat(currentPrice).isEqualByComparingTo(maxBid);
    }

    @RepeatedConcurrencyTest
    @DisplayName("INT-CON-03 BR-15: tạo 2 phiên đồng thời cùng sản phẩm -> 1 thành công (201), 1 bị 409")
    void testTwoConcurrentAuctionsSameProduct() throws Exception {
        Product product = seed.createProduct(seller, category, "Unique Watch", ProductStatus.AVAILABLE);
        Instant start = mutableClock.instant().plus(1, ChronoUnit.HOURS);
        Instant end = start.plus(2, ChronoUnit.HOURS);

        Map<String, Object> body = Map.of(
                "productId", product.getId(),
                "startPrice", 500000,
                "bidIncrement", 10000,
                "startTime", start.toString(),
                "endTime", end.toString()
        );

        List<ResponseEntity<String>> results = Concurrently.run(2, () -> api.as(seller).post("/auctions", body));

        long created = results.stream().filter(r -> r.getStatusCode().value() == 201).count();
        long conflict = results.stream().filter(r -> r.getStatusCode().value() == 409).count();

        assertThat(created).isEqualTo(1L);
        assertThat(conflict).isEqualTo(1L);
    }

    @RepeatedConcurrencyTest
    @DisplayName("INT-CON-04 Hai tiến trình chốt cùng phiên -> đúng 1 Order")
    void testTwoProcessesClosingSameAuction() throws Exception {
        Instant now = mutableClock.instant();
        Product product = seed.createProduct(seller, category, "Painting", ProductStatus.IN_AUCTION);
        Auction auction = seed.createAuction(seller, product, new BigDecimal("500000"), new BigDecimal("10000"),
                now.minus(20, ChronoUnit.MINUTES), now.plus(5, ChronoUnit.MINUTES), AuctionStatus.ACTIVE);

        api.as(buyer).post("/auctions/" + auction.getId() + "/bids", Map.of("amount", 510000));
        mutableClock.advance(Duration.ofMinutes(10));

        Concurrently.run(2, () -> {
            auctionScheduler.scheduleAuctionStatusTransitions();
            return null;
        });

        Long orderCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE auction_id = ?", Long.class, auction.getId()
        );
        assertThat(orderCount).isEqualTo(1L);
    }
}
