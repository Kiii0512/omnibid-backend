package com.omnibid.it;

import com.omnibid.entity.Order;
import com.omnibid.support.BaseIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatabaseConstraintIT extends BaseIT {

    @Test
    @DisplayName("INT-DB-01 Ràng buộc Order: trùng auction_id, seller = buyer, final_price = 0")
    void testOrderConstraints() {
        Order order = seed.orderPendingPayment(buyer, seller);

        // 1. Order thứ hai cùng auction_id -> DuplicateKeyException / DataIntegrityViolationException
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO orders (auction_id, buyer_id, seller_id, product_id, final_price, status, payment_deadline) " +
                "VALUES (?, ?, ?, ?, ?, 'PENDING_PAYMENT', NOW())",
                order.getAuctionId(), buyer.getId(), seller.getId(), order.getProductId(), new BigDecimal("600000")
        )).isInstanceOf(DataAccessException.class);

        // 2. Order seller_id = buyer_id -> CHECK constraint chk_orders_parties
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO orders (auction_id, buyer_id, seller_id, product_id, final_price, status, payment_deadline) " +
                "VALUES (?, ?, ?, ?, ?, 'PENDING_PAYMENT', NOW())",
                999999L, buyer.getId(), buyer.getId(), order.getProductId(), new BigDecimal("600000")
        )).isInstanceOf(DataAccessException.class);

        // 3. Order final_price = 0 -> CHECK constraint chk_orders_final_price
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO orders (auction_id, buyer_id, seller_id, product_id, final_price, status, payment_deadline) " +
                "VALUES (?, ?, ?, ?, ?, 'PENDING_PAYMENT', NOW())",
                999998L, buyer.getId(), seller.getId(), order.getProductId(), BigDecimal.ZERO
        )).isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("INT-DB-02 Ràng buộc Payment: 2 SUCCESS cùng đơn bị cấm, cùng idempotency cho 2 đơn được phép")
    void testPaymentConstraints() {
        Order order1 = seed.orderPendingPayment(buyer, seller);
        Order order2 = seed.orderPendingPayment(bidderB, seller);

        String sameKey = "IDEMP-" + UUID.randomUUID();

        // Chèn SUCCESS cho order1
        jdbcTemplate.update(
                "INSERT INTO payments (order_id, attempt_no, amount, method, status, idempotency_key, transaction_ref, paid_at) " +
                "VALUES (?, 1, ?, 'MOCK_CARD', 'SUCCESS', ?, ?, NOW())",
                order1.getId(), order1.getFinalPrice(), sameKey, "TX-" + UUID.randomUUID()
        );

        // 1. Chèn SUCCESS thứ hai cho order1 -> vi phạm uq_payments_one_success
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO payments (order_id, attempt_no, amount, method, status, idempotency_key, transaction_ref, paid_at) " +
                "VALUES (?, 2, ?, 'MOCK_CARD', 'SUCCESS', ?, ?, NOW())",
                order1.getId(), order1.getFinalPrice(), "OTHER-KEY", "TX-" + UUID.randomUUID()
        )).isInstanceOf(DataIntegrityViolationException.class);

        // 2. Cùng idempotency_key cho order2 khác nhau -> ĐƯỢC PHÉP
        int rows = jdbcTemplate.update(
                "INSERT INTO payments (order_id, attempt_no, amount, method, status, idempotency_key, transaction_ref, paid_at) " +
                "VALUES (?, 1, ?, 'MOCK_CARD', 'SUCCESS', ?, ?, NOW())",
                order2.getId(), order2.getFinalPrice(), sameKey, "TX-" + UUID.randomUUID()
        );
        assertThat(rows).isEqualTo(1);

        // Cập nhật trạng thái order để giữ invariant sạch cho BaseIT
        jdbcTemplate.update("UPDATE orders SET status = 'PAID' WHERE id IN (?, ?)", order1.getId(), order2.getId());
        jdbcTemplate.update("INSERT INTO shipments (order_id, tracking_code, carrier_name, status) VALUES (?, ?, 'SIM', 'CREATED')",
                order1.getId(), "TRK1-" + UUID.randomUUID());
        jdbcTemplate.update("INSERT INTO shipments (order_id, tracking_code, carrier_name, status) VALUES (?, ?, 'SIM', 'CREATED')",
                order2.getId(), "TRK2-" + UUID.randomUUID());
        Long s1 = jdbcTemplate.queryForObject("SELECT id FROM shipments WHERE order_id = ?", Long.class, order1.getId());
        Long s2 = jdbcTemplate.queryForObject("SELECT id FROM shipments WHERE order_id = ?", Long.class, order2.getId());
        jdbcTemplate.update("INSERT INTO shipment_events (shipment_id, status, note) VALUES (?, 'CREATED', 'Init')", s1);
        jdbcTemplate.update("INSERT INTO shipment_events (shipment_id, status, note) VALUES (?, 'CREATED', 'Init')", s2);
    }

    @Test
    @DisplayName("INT-DB-03 Ràng buộc Payment: 2 PENDING cùng đơn bị cấm, PENDING->FAILED rồi PENDING mới được phép")
    void testPaymentPendingConstraints() {
        Order order = seed.orderPendingPayment(buyer, seller);

        // Chèn PENDING lần 1
        jdbcTemplate.update(
                "INSERT INTO payments (order_id, attempt_no, amount, method, status, idempotency_key, transaction_ref) " +
                "VALUES (?, 1, ?, 'MOCK_CARD', 'PENDING', ?, ?)",
                order.getId(), order.getFinalPrice(), "KEY-1", "TX-1"
        );

        // 1. Chèn PENDING thứ hai cùng đơn -> vi phạm uq_payments_one_pending
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO payments (order_id, attempt_no, amount, method, status, idempotency_key, transaction_ref) " +
                "VALUES (?, 2, ?, 'MOCK_CARD', 'PENDING', ?, ?)",
                order.getId(), order.getFinalPrice(), "KEY-2", "TX-2"
        )).isInstanceOf(DataIntegrityViolationException.class);

        // 2. Chuyển lần 1 thành FAILED
        jdbcTemplate.update("UPDATE payments SET status = 'FAILED', failure_reason = 'TEST' WHERE order_id = ? AND attempt_no = 1", order.getId());

        // 3. Chèn PENDING mới -> ĐƯỢC PHÉP
        int rows = jdbcTemplate.update(
                "INSERT INTO payments (order_id, attempt_no, amount, method, status, idempotency_key, transaction_ref) " +
                "VALUES (?, 2, ?, 'MOCK_CARD', 'PENDING', ?, ?)",
                order.getId(), order.getFinalPrice(), "KEY-3", "TX-3"
        );
        assertThat(rows).isEqualTo(1);

        // Dọn payment PENDING trước khi InvariantCheck chạy (INV-7)
        jdbcTemplate.update("UPDATE payments SET status = 'FAILED' WHERE order_id = ? AND attempt_no = 2", order.getId());
    }

    @Test
    @DisplayName("INT-DB-04 Ràng buộc FK Category RESTRICT và CHECK chk_reports_target")
    void testCategoryAndReportConstraints() {
        Order order = seed.orderPendingPayment(buyer, seller);

        // 1. Xóa Category còn Product -> vi phạm FK RESTRICT
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM categories WHERE id = ?", category.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);

        // 2. Report VIOLATION không có target nào -> vi phạm CHECK chk_reports_target
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO reports (reporter_id, report_type, reason, status) VALUES (?, 'VIOLATION', 'Test reason 123', 'PENDING')",
                buyer.getId()
        )).isInstanceOf(DataAccessException.class);

        // 3. Report COMPLAINT không có order_id -> vi phạm CHECK chk_reports_target
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO reports (reporter_id, report_type, reason, status, reported_user_id) " +
                "VALUES (?, 'COMPLAINT', 'Complaint reason 123', 'PENDING', ?)",
                buyer.getId(), seller.getId()
        )).isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("INT-DB-05 Ràng buộc Shipment: 2 Shipment cùng đơn bị cấm, tracking_code trùng bị cấm")
    void testShipmentConstraints() {
        Order order1 = seed.orderPaid(buyer, seller);
        Order order2 = seed.orderPaid(bidderB, seller);

        String existingTracking = jdbcTemplate.queryForObject(
                "SELECT tracking_code FROM shipments WHERE order_id = ?", String.class, order1.getId());

        // 1. Shipment thứ hai cùng order_id -> vi phạm UNIQUE uq_shipments_order
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO shipments (order_id, tracking_code, carrier_name, status) VALUES (?, ?, 'SIM', 'CREATED')",
                order1.getId(), "NEW-TRACK-" + UUID.randomUUID()
        )).isInstanceOf(DataIntegrityViolationException.class);

        // 2. tracking_code trùng -> vi phạm UNIQUE uq_shipments_tracking
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO shipments (order_id, tracking_code, carrier_name, status) VALUES (?, ?, 'SIM', 'CREATED')",
                999999L, existingTracking
        )).isInstanceOf(DataIntegrityViolationException.class);
    }
}
