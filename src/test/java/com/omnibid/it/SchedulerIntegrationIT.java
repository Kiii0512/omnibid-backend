package com.omnibid.it;

import com.omnibid.entity.Auction;
import com.omnibid.entity.Order;
import com.omnibid.entity.Product;
import com.omnibid.enums.AuctionStatus;
import com.omnibid.enums.ProductStatus;
import com.omnibid.service.AuctionService;
import com.omnibid.service.OrderService;
import com.omnibid.support.BaseIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;

public class SchedulerIntegrationIT extends BaseIT {

    @Autowired
    private AuctionService auctionService;

    @MockitoSpyBean
    private OrderService orderService;

    @Test
    @DisplayName("INT-SCH-01 Chạy lại OrderScheduler.tick() không có tác dụng phụ (idempotent)")
    void testOrderSchedulerTickIdempotent() {
        Order order = seed.orderPendingPayment(buyer, seller);
        mutableClock.advance(Duration.ofHours(49));

        orderScheduler.tick();
        Long notifCount1 = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE ref_type = 'ORDER' AND ref_id = ?",
                Long.class, order.getId()
        );
        assertThat(notifCount1).isGreaterThanOrEqualTo(1L);

        orderScheduler.tick();
        orderScheduler.tick();
        Long notifCountAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE ref_type = 'ORDER' AND ref_id = ?",
                Long.class, order.getId()
        );
        assertThat(notifCountAfter).isEqualTo(notifCount1);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, order.getId()))
                .isEqualTo("EXPIRED");
    }

    @Test
    @DisplayName("INT-SCH-02 Một phiên lỗi không kéo theo cả lô")
    void testOneFailingAuctionDoesNotRollbackBatch() {
        Instant now = mutableClock.instant();

        Product p1 = seed.createProduct(seller, category, "P1", ProductStatus.IN_AUCTION);
        Auction a1 = seed.createAuction(seller, p1, new BigDecimal("100000"), new BigDecimal("10000"),
                now.minus(10, ChronoUnit.MINUTES), now.plus(5, ChronoUnit.MINUTES), AuctionStatus.ACTIVE);
        seed.createBid(a1, buyer, new BigDecimal("120000"));

        Product p2 = seed.createProduct(seller, category, "P2", ProductStatus.IN_AUCTION);
        Auction a2 = seed.createAuction(seller, p2, new BigDecimal("100000"), new BigDecimal("10000"),
                now.minus(10, ChronoUnit.MINUTES), now.plus(5, ChronoUnit.MINUTES), AuctionStatus.ACTIVE);
        seed.createBid(a2, buyer, new BigDecimal("120000"));

        Product p3 = seed.createProduct(seller, category, "P3", ProductStatus.IN_AUCTION);
        Auction a3 = seed.createAuction(seller, p3, new BigDecimal("100000"), new BigDecimal("10000"),
                now.minus(10, ChronoUnit.MINUTES), now.plus(5, ChronoUnit.MINUTES), AuctionStatus.ACTIVE);
        seed.createBid(a3, buyer, new BigDecimal("120000"));

        doThrow(new RuntimeException("Simulated error in createFromAuction for a2"))
                .when(orderService).createFromAuction(argThat(a -> a != null && a.getId().equals(a2.getId())), any());

        mutableClock.advance(Duration.ofHours(2));
        auctionService.closeEndedAuctions();

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM auctions WHERE id = ?", String.class, a1.getId())).isEqualTo("ENDED");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM auctions WHERE id = ?", String.class, a2.getId())).isEqualTo("ACTIVE");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM auctions WHERE id = ?", String.class, a3.getId())).isEqualTo("ENDED");
    }
}
