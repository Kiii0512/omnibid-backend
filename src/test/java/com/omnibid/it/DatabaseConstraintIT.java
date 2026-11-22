package com.omnibid.it;

import com.omnibid.entity.Order;
import com.omnibid.entity.Product;
import com.omnibid.enums.ProductStatus;
import com.omnibid.support.BaseIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatabaseConstraintIT extends BaseIT {

    @Test
    @DisplayName("INT-DB-01 Ràng buộc Order: trùng auction_id, seller = buyer, final_price = 0")
    void testOrderConstraints() {
        Order order = seed.orderPendingPayment(buyer, seller);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO orders (auction_id, buyer_id, seller_id, product_id, final_price, status, payment_deadline) " +
                "VALUES (?, ?, ?, ?, ?, 'PENDING_PAYMENT', NOW())",
                order.getAuctionId(), buyer.getId(), seller.getId(), order.getProductId(), new BigDecimal("600000")
        )).isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO orders (auction_id, buyer_id, seller_id, product_id, final_price, status, payment_deadline) " +
                "VALUES (?, ?, ?, ?, ?, 'PENDING_PAYMENT', NOW())",
                999999L, buyer.getId(), buyer.getId(), order.getProductId(), new BigDecimal("600000")
        )).isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO orders (auction_id, buyer_id, seller_id, product_id, final_price, status, payment_deadline) " +
                "VALUES (?, ?, ?, ?, ?, 'PENDING_PAYMENT', NOW())",
                999998L, buyer.getId(), seller.getId(), order.getProductId(), BigDecimal.ZERO
        )).isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("INT-DB-02 Ràng buộc Payment v4: 2 SUCCESS cùng đơn bị cấm, cùng idempotency cho 2 đơn được phép")
    void testPaymentConstraints() {
        Order order1 = seed.orderPendingPayment(buyer, seller);
        Order order2 = seed.orderPendingPayment(bidderB, seller);

        String sameKey = UUID.randomUUID().toString();

        jdbcTemplate.update(
                "INSERT INTO payments (order_id, attempt_no, amount, method, status, idempotency_key, paid_at) " +
                "VALUES (?, 1, ?, 'MOCK_CARD', 'SUCCESS', ?, NOW())",
                order1.getId(), order1.getFinalPrice(), sameKey
        );

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO payments (order_id, attempt_no, amount, method, status, idempotency_key, paid_at) " +
                "VALUES (?, 2, ?, 'MOCK_CARD', 'SUCCESS', ?, NOW())",
                order1.getId(), order1.getFinalPrice(), UUID.randomUUID().toString()
        )).isInstanceOf(DataIntegrityViolationException.class);

        int rows = jdbcTemplate.update(
                "INSERT INTO payments (order_id, attempt_no, amount, method, status, idempotency_key, paid_at) " +
                "VALUES (?, 1, ?, 'MOCK_CARD', 'SUCCESS', ?, NOW())",
                order2.getId(), order2.getFinalPrice(), sameKey
        );
        assertThat(rows).isEqualTo(1);

        jdbcTemplate.update("UPDATE orders SET status = 'PAID' WHERE id IN (?, ?)", order1.getId(), order2.getId());
    }

    @Test
    @DisplayName("INT-DB-03 BR-15: cột sinh active_product_id cấm 2 phiên SCHEDULED/ACTIVE cùng sản phẩm")
    void testUniqueActiveProductConstraint() {
        Product product = seed.createProduct(seller, category, "Single Active Product", ProductStatus.IN_AUCTION);

        jdbcTemplate.update(
                "INSERT INTO auctions (product_id, seller_id, start_price, bid_increment, current_price, bid_count, start_time, end_time, status) " +
                "VALUES (?, ?, 100000, 10000, 100000, 0, NOW() + INTERVAL 1 HOUR, NOW() + INTERVAL 2 HOUR, 'SCHEDULED')",
                product.getId(), seller.getId()
        );

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO auctions (product_id, seller_id, start_price, bid_increment, current_price, bid_count, start_time, end_time, status) " +
                "VALUES (?, ?, 100000, 10000, 100000, 0, NOW() + INTERVAL 3 HOUR, NOW() + INTERVAL 4 HOUR, 'ACTIVE')",
                product.getId(), seller.getId()
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("INT-DB-04 Ràng buộc FK Category RESTRICT và CHECK chk_reports_target")
    void testCategoryAndReportConstraints() {
        seed.orderPendingPayment(buyer, seller);

        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM categories WHERE id = ?", category.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO reports (reporter_id, reason, status) VALUES (?, 'Test reason', 'PENDING')",
                buyer.getId()
        )).isInstanceOf(DataAccessException.class);
    }
}
