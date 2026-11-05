package com.omnibid.it;

import com.omnibid.entity.Auction;
import com.omnibid.entity.Order;
import com.omnibid.entity.Payment;
import com.omnibid.enums.AuctionStatus;
import com.omnibid.enums.OrderStatus;
import com.omnibid.enums.PaymentMethod;
import com.omnibid.enums.PaymentStatus;
import com.omnibid.enums.ProductStatus;
import com.omnibid.repository.PaymentRepository;
import com.omnibid.scheduler.AuctionScheduler;
import com.omnibid.scheduler.OrderScheduler;
import com.omnibid.service.AuctionService;
import com.omnibid.service.OrderService;
import com.omnibid.support.BaseIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

public class SchedulerIntegrationIT extends BaseIT {

    @Autowired
    private OrderScheduler orderScheduler;

    @Autowired
    private AuctionScheduler auctionScheduler;

    @Autowired
    private AuctionService auctionService;

    @Autowired
    private PaymentRepository paymentRepository;

    @MockitoSpyBean
    private OrderService orderService;

    @Test
    @DisplayName("INT-SCH-01 Thứ tự trong OrderScheduler.tick(): Payment TIMEOUT trước rồi Order EXPIRED")
    void testOrderSchedulerTickOrdering() {
        Order order = seed.orderPendingPayment(buyer, seller);

        // Tạo Payment PENDING 6 phút trước (payment pending timeout mặc định 5 phút)
        LocalDateTime sixMinutesAgo = LocalDateTime.now(mutableClock).minusMinutes(6);
        Payment payment = Payment.builder()
                .orderId(order.getId())
                .attemptNo(1)
                .amount(order.getFinalPrice())
                .method(PaymentMethod.MOCK_CARD)
                .status(PaymentStatus.PENDING)
                .idempotencyKey("KEY-SCH-01-" + UUID.randomUUID())
                .transactionRef("TX-SCH-01-" + UUID.randomUUID())
                .createdAt(sixMinutesAgo)
                .build();
        paymentRepository.save(payment);

        // Advance mutableClock qua deadline của Order (48h)
        mutableClock.advance(Duration.ofHours(49));

        // Gọi tick() 1 lần duy nhất
        orderScheduler.tick();

        // 1. Payment phải chuyển thành FAILED (TIMEOUT)
        Payment updatedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(updatedPayment.getFailureReason()).isEqualTo("TIMEOUT");

        // 2. Order phải chuyển thành EXPIRED
        String orderStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM orders WHERE id = ?", String.class, order.getId()
        );
        assertThat(orderStatus).isEqualTo("EXPIRED");

        // 3. Product phải về AVAILABLE
        String productStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM products WHERE id = ?", String.class, order.getProductId()
        );
        assertThat(productStatus).isEqualTo("AVAILABLE");
    }

    @Test
    @DisplayName("INT-SCH-02 Chạy lại tick() không tác dụng phụ (idempotent)")
    void testOrderSchedulerTickIdempotent() {
        Order order = seed.orderPendingPayment(buyer, seller);

        // Đẩy thời gian qua 48h để order hết hạn
        mutableClock.advance(Duration.ofHours(49));

        // Tick lần 1
        orderScheduler.tick();

        Long notifCount1 = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE ref_type = 'ORDER' AND ref_id = ?",
                Long.class, order.getId()
        );
        assertThat(notifCount1).isGreaterThanOrEqualTo(1);

        // Tick lần 2 và 3 liên tiếp
        orderScheduler.tick();
        orderScheduler.tick();

        Long notifCountAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE ref_type = 'ORDER' AND ref_id = ?",
                Long.class, order.getId()
        );
        // Số thông báo không tăng thêm
        assertThat(notifCountAfter).isEqualTo(notifCount1);

        String orderStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM orders WHERE id = ?", String.class, order.getId()
        );
        assertThat(orderStatus).isEqualTo("EXPIRED");
    }

    @Autowired
    private com.omnibid.repository.AuctionRepository auctionRepository;

    @Test
    @DisplayName("INT-SCH-03 Một phiên lỗi không kéo theo cả lô (G-04, NFR-06)")
    void testOneFailingAuctionDoesNotRollbackBatch() {
        LocalDateTime now = LocalDateTime.now(mutableClock);

        // Tạo 3 auction có bid cao hơn start price và end time trong tương lai gần (sẽ hết hạn khi advance)
        com.omnibid.entity.Product p1 = seed.createProduct(seller, category, "P1", ProductStatus.IN_AUCTION);
        Auction a1 = seed.createAuction(seller, p1, new BigDecimal("100000"), new BigDecimal("10000"),
                now.minusMinutes(10), now.plusMinutes(5), AuctionStatus.ACTIVE);
        seed.createBid(a1, buyer, new BigDecimal("120000"), com.omnibid.enums.BidStatus.LEADING);
        a1.setCurrentPrice(new BigDecimal("120000"));
        a1.setHighestBidderId(buyer.getId());
        a1.setBidCount(1);
        auctionRepository.save(a1);

        com.omnibid.entity.Product p2 = seed.createProduct(seller, category, "P2", ProductStatus.IN_AUCTION);
        Auction a2 = seed.createAuction(seller, p2, new BigDecimal("100000"), new BigDecimal("10000"),
                now.minusMinutes(10), now.plusMinutes(5), AuctionStatus.ACTIVE);
        seed.createBid(a2, buyer, new BigDecimal("120000"), com.omnibid.enums.BidStatus.LEADING);
        a2.setCurrentPrice(new BigDecimal("120000"));
        a2.setHighestBidderId(buyer.getId());
        a2.setBidCount(1);
        auctionRepository.save(a2);

        com.omnibid.entity.Product p3 = seed.createProduct(seller, category, "P3", ProductStatus.IN_AUCTION);
        Auction a3 = seed.createAuction(seller, p3, new BigDecimal("100000"), new BigDecimal("10000"),
                now.minusMinutes(10), now.plusMinutes(5), AuctionStatus.ACTIVE);
        seed.createBid(a3, buyer, new BigDecimal("120000"), com.omnibid.enums.BidStatus.LEADING);
        a3.setCurrentPrice(new BigDecimal("120000"));
        a3.setHighestBidderId(buyer.getId());
        a3.setBidCount(1);
        auctionRepository.save(a3);

        // Spy OrderService: phiên a2 ném RuntimeException khi createFromAuction
        doThrow(new RuntimeException("Simulated error in createFromAuction for a2"))
                .when(orderService).createFromAuction(org.mockito.ArgumentMatchers.argThat(a -> a != null && a.getId().equals(a2.getId())));

        // Đẩy clock vượt qua endTime của cả 3 phiên
        mutableClock.advance(Duration.ofHours(2));

        // Chạy closeEndedAuctions
        auctionService.closeEndedAuctions();

        // Kiểm tra a1: ENDED và có order
        String status1 = jdbcTemplate.queryForObject("SELECT status FROM auctions WHERE id = ?", String.class, a1.getId());
        assertThat(status1).isEqualTo("ENDED");
        Long orderCount1 = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders WHERE auction_id = ?", Long.class, a1.getId());
        assertThat(orderCount1).isEqualTo(1L);

        // Kiểm tra a2: vẫn ACTIVE (bị rollback riêng biệt để lần sau thử lại)
        String status2 = jdbcTemplate.queryForObject("SELECT status FROM auctions WHERE id = ?", String.class, a2.getId());
        assertThat(status2).isEqualTo("ACTIVE");
        Long orderCount2 = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders WHERE auction_id = ?", Long.class, a2.getId());
        assertThat(orderCount2).isEqualTo(0L);

        // Kiểm tra a3: ENDED và có order
        String status3 = jdbcTemplate.queryForObject("SELECT status FROM auctions WHERE id = ?", String.class, a3.getId());
        assertThat(status3).isEqualTo("ENDED");
        Long orderCount3 = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders WHERE auction_id = ?", Long.class, a3.getId());
        assertThat(orderCount3).isEqualTo(1L);
    }
}
