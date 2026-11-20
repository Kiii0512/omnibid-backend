package com.omnibid.it;

import com.omnibid.dto.payment.PaymentResponse;
import com.omnibid.entity.Order;
import com.omnibid.entity.Payment;
import com.omnibid.entity.User;
import com.omnibid.enums.OrderStatus;
import com.omnibid.enums.PaymentStatus;
import com.omnibid.enums.UserStatus;
import com.omnibid.scheduler.OrderScheduler;
import com.omnibid.support.BaseIT;
import com.omnibid.support.Concurrently;
import com.omnibid.support.CountingMockPaymentGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class PaymentIntegrationIT extends BaseIT {

    @Autowired
    private CountingMockPaymentGateway gateway;

    @Autowired
    private OrderScheduler orderScheduler;

    @BeforeEach
    void setupGateway() {
        gateway.reset();
    }

    @Test
    @DisplayName("INT-PAY-01 Thành công ghi đủ dữ liệu (Payment, Order, Shipment, ShipmentEvent)")
    void testPaySuccessWritesAllEntities() {
        Order order = seed.orderPendingPayment(buyer, seller);
        String key = "KEY-" + UUID.randomUUID();

        ResponseEntity<String> res = api.as(buyer).post(
                "/orders/" + order.getId() + "/payments",
                Map.of("method", "MOCK_CARD", "simulateResult", "SUCCESS"),
                Map.of("Idempotency-Key", key)
        );

        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();

        // 1 Payment SUCCESS (attempt_no=1, paid_at, transaction_ref MOCK-...)
        List<Map<String, Object>> payments = jdbcTemplate.queryForList(
                "SELECT * FROM payments WHERE order_id = ?", order.getId()
        );
        assertThat(payments).hasSize(1);
        Map<String, Object> p = payments.get(0);
        assertThat(p.get("status")).isEqualTo("SUCCESS");
        assertThat(p.get("attempt_no")).isEqualTo(1);
        assertThat(p.get("paid_at")).isNotNull();
        assertThat((String) p.get("transaction_ref")).startsWith("MOCK-");

        // Order PAID
        String orderStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM orders WHERE id = ?", String.class, order.getId()
        );
        assertThat(orderStatus).isEqualTo("PAID");

        // 1 Shipment CREATED
        List<Map<String, Object>> shipments = jdbcTemplate.queryForList(
                "SELECT * FROM shipments WHERE order_id = ?", order.getId()
        );
        assertThat(shipments).hasSize(1);
        Map<String, Object> s = shipments.get(0);
        assertThat(s.get("status")).isEqualTo("CREATED");
        Long shipmentId = (Long) s.get("id");

        // 1 shipment_events CREATED updated_by NULL
        List<Map<String, Object>> events = jdbcTemplate.queryForList(
                "SELECT * FROM shipment_events WHERE shipment_id = ?", shipmentId
        );
        assertThat(events).hasSize(1);
        Map<String, Object> ev = events.get(0);
        assertThat(ev.get("status")).isEqualTo("CREATED");
        assertThat(ev.get("updated_by")).isNull();
    }

    @Test
    @DisplayName("INT-PAY-02 Thất bại rồi thành công: 2 attempts, đúng 1 SUCCESS")
    void testPayFailedThenSuccess() {
        Order order = seed.orderPendingPayment(buyer, seller);

        // Attempt 1: FAILED
        String key1 = "KEY-" + UUID.randomUUID();
        ResponseEntity<String> res1 = api.as(buyer).post(
                "/orders/" + order.getId() + "/payments",
                Map.of("method", "MOCK_CARD", "simulateResult", "FAILED"),
                Map.of("Idempotency-Key", key1)
        );
        assertThat(res1.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(res1.getBody()).contains("FAILED");

        // Attempt 2: SUCCESS
        String key2 = "KEY-" + UUID.randomUUID();
        ResponseEntity<String> res2 = api.as(buyer).post(
                "/orders/" + order.getId() + "/payments",
                Map.of("method", "MOCK_CARD", "simulateResult", "SUCCESS"),
                Map.of("Idempotency-Key", key2)
        );
        assertThat(res2.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(res2.getBody()).contains("SUCCESS");

        // Verify DB
        List<Map<String, Object>> payments = jdbcTemplate.queryForList(
                "SELECT attempt_no, status FROM payments WHERE order_id = ? ORDER BY attempt_no ASC", order.getId()
        );
        assertThat(payments).hasSize(2);
        assertThat(payments.get(0).get("attempt_no")).isEqualTo(1);
        assertThat(payments.get(0).get("status")).isEqualTo("FAILED");
        assertThat(payments.get(1).get("attempt_no")).isEqualTo(2);
        assertThat(payments.get(1).get("status")).isEqualTo("SUCCESS");

        String orderStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM orders WHERE id = ?", String.class, order.getId()
        );
        assertThat(orderStatus).isEqualTo("PAID");
    }

    @Test
    @DisplayName("INT-PAY-03 Cùng key, 10 yêu cầu đồng thời chỉ tạo 1 Payment")
    void testSameKeyConcurrentTenRequests() throws Exception {
        Order order = seed.orderPendingPayment(buyer, seller);
        String key = "KEY-" + UUID.randomUUID();

        List<ResponseEntity<String>> results = Concurrently.run(10, () ->
                api.as(buyer).post(
                        "/orders/" + order.getId() + "/payments",
                        Map.of("method", "MOCK_CARD", "simulateResult", "SUCCESS"),
                        Map.of("Idempotency-Key", key)
                )
        );

        assertThat(results).allSatisfy(r ->
                assertThat(r.getStatusCode().value()).isIn(200, 202)
        );

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payments WHERE order_id = ?", Long.class, order.getId()
        );
        assertThat(count).isEqualTo(1L);
        assertThat(gateway.chargeCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("INT-PAY-04 Hai key khác nhau đồng thời: 1 thành công/tiến trình, 1 bị 409 PAYMENT_IN_PROGRESS")
    void testTwoDifferentKeysConcurrently() throws Exception {
        Order order = seed.orderPendingPayment(buyer, seller);
        gateway.setDelayMs(500);

        String key1 = "KEY-A-" + UUID.randomUUID();
        String key2 = "KEY-B-" + UUID.randomUUID();

        List<ResponseEntity<String>> results = Concurrently.runIndexed(2, (index) -> {
            String key = index == 0 ? key1 : key2;
            return api.as(buyer).post(
                    "/orders/" + order.getId() + "/payments",
                    Map.of("method", "MOCK_CARD", "simulateResult", "SUCCESS"),
                    Map.of("Idempotency-Key", key)
            );
        });

        int successCount = 0;
        int conflictCount = 0;
        for (ResponseEntity<String> r : results) {
            if (r.getStatusCode().value() == 200 || r.getStatusCode().value() == 202) {
                successCount++;
            } else if (r.getStatusCode().value() == 409) {
                conflictCount++;
                assertThat(r.getBody()).contains("PAYMENT_IN_PROGRESS");
            }
        }

        assertThat(successCount).isEqualTo(1);
        assertThat(conflictCount).isEqualTo(1);
    }

    @Test
    @DisplayName("INT-PAY-05 Giới hạn tốc độ qua API: 5 lần FAILED, lần 6 bị 429 Retry-After, advance và lần 7 thành công")
    void testPaymentRateLimiting() {
        Order order = seed.orderPendingPayment(buyer, seller);

        // 5 failed payments with different keys
        for (int i = 0; i < 5; i++) {
            ResponseEntity<String> res = api.as(buyer).post(
                    "/orders/" + order.getId() + "/payments",
                    Map.of("method", "MOCK_CARD", "simulateResult", "FAILED"),
                    Map.of("Idempotency-Key", "KEY-FAIL-" + i + "-" + UUID.randomUUID())
            );
            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        }

        // 6th attempt -> 429
        ResponseEntity<String> res6 = api.as(buyer).post(
                "/orders/" + order.getId() + "/payments",
                Map.of("method", "MOCK_CARD", "simulateResult", "FAILED"),
                Map.of("Idempotency-Key", "KEY-FAIL-6-" + UUID.randomUUID())
        );
        assertThat(res6.getStatusCode().value()).isEqualTo(429);
        List<String> retryHeaders = res6.getHeaders().get("Retry-After");
        assertThat(retryHeaders).isNotEmpty();
        long retryAfterSeconds = Long.parseLong(retryHeaders.get(0));
        assertThat(retryAfterSeconds).isGreaterThan(0);

        // Advance past window (10 minutes)
        mutableClock.advance(Duration.ofSeconds(retryAfterSeconds + 2));

        // 7th attempt -> SUCCESS
        ResponseEntity<String> res7 = api.as(buyer).post(
                "/orders/" + order.getId() + "/payments",
                Map.of("method", "MOCK_CARD", "simulateResult", "SUCCESS"),
                Map.of("Idempotency-Key", "KEY-SUCCESS-" + UUID.randomUUID())
        );
        assertThat(res7.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(res7.getBody()).contains("SUCCESS");
    }

    @Test
    @DisplayName("INT-PAY-06 Không thể có 2 SUCCESS: 20 luồng key khác nhau, đúng 1 Payment SUCCESS")
    void testTwentyConcurrentDifferentKeysOnlyOneSuccess() throws Exception {
        Order order = seed.orderPendingPayment(buyer, seller);

        List<ResponseEntity<String>> results = Concurrently.runIndexed(20, (index) -> {
            String key = "KEY-CONC-" + index + "-" + UUID.randomUUID();
            return api.as(buyer).post(
                    "/orders/" + order.getId() + "/payments",
                    Map.of("method", "MOCK_CARD", "simulateResult", "SUCCESS"),
                    Map.of("Idempotency-Key", key)
            );
        });

        long successPayments = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payments WHERE order_id = ? AND status = 'SUCCESS'", Long.class, order.getId()
        );
        assertThat(successPayments).isEqualTo(1L);

        long shipments = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shipments WHERE order_id = ?", Long.class, order.getId()
        );
        assertThat(shipments).isEqualTo(1L);

        // Các kết quả còn lại phải là 409
        int successResponses = 0;
        int conflictResponses = 0;
        for (ResponseEntity<String> r : results) {
            if (r.getStatusCode().is2xxSuccessful()) {
                successResponses++;
            } else if (r.getStatusCode().value() == 409) {
                conflictResponses++;
            }
        }
        assertThat(successResponses).isGreaterThanOrEqualTo(1);
        assertThat(conflictResponses).isGreaterThan(0);
    }

    @Test
    @DisplayName("INT-PAY-07 Thứ tự kiểm tra: (a) phát lại key cũ khi EXPIRED, (b) key mới khi EXPIRED + buyer BANNED, (c) OTHER gọi")
    void testPaymentDecisionTableCheckOrder() {
        Order order = seed.orderPendingPayment(buyer, seller);

        // Thành công lần 1 với key cũ
        String oldKey = "KEY-OLD-" + UUID.randomUUID();
        ResponseEntity<String> firstRes = api.as(buyer).post(
                "/orders/" + order.getId() + "/payments",
                Map.of("method", "MOCK_CARD", "simulateResult", "FAILED"),
                Map.of("Idempotency-Key", oldKey)
        );
        assertThat(firstRes.getStatusCode().is2xxSuccessful()).isTrue();

        // Đổi trạng thái order sang EXPIRED
        jdbcTemplate.update("UPDATE orders SET status = 'EXPIRED' WHERE id = ?", order.getId());

        // (a) Phát lại key cũ sau khi đơn EXPIRED -> trả kết quả cũ (200 FAILED)
        ResponseEntity<String> replayRes = api.as(buyer).post(
                "/orders/" + order.getId() + "/payments",
                Map.of("method", "MOCK_CARD", "simulateResult", "SUCCESS"),
                Map.of("Idempotency-Key", oldKey)
        );
        assertThat(replayRes.getStatusCode().value()).isEqualTo(200);
        assertThat(replayRes.getBody()).contains("FAILED");

        // (b) Khóa buyer; gọi với key mới -> 403 ACCOUNT_BANNED (kiểm tra tài khoản banned trước khi kiểm tra EXPIRED)
        jdbcTemplate.update("UPDATE users SET status = 'BANNED' WHERE id = ?", buyer.getId());
        String newKey = "KEY-NEW-" + UUID.randomUUID();
        ResponseEntity<String> bannedRes = api.as(buyer).post(
                "/orders/" + order.getId() + "/payments",
                Map.of("method", "MOCK_CARD", "simulateResult", "SUCCESS"),
                Map.of("Idempotency-Key", newKey)
        );
        assertThat(bannedRes.getStatusCode().value()).isEqualTo(403);
        assertThat(bannedRes.getBody()).contains("ACCOUNT_BANNED");

        // Khôi phục buyer để tránh ảnh hưởng invariant
        jdbcTemplate.update("UPDATE users SET status = 'ACTIVE' WHERE id = ?", buyer.getId());

        // (c) OTHER gọi đơn EXPIRED -> 403 FORBIDDEN
        ResponseEntity<String> otherRes = api.as(other).post(
                "/orders/" + order.getId() + "/payments",
                Map.of("method", "MOCK_CARD", "simulateResult", "SUCCESS"),
                Map.of("Idempotency-Key", "KEY-OTHER-" + UUID.randomUUID())
        );
        assertThat(otherRes.getStatusCode().value()).isEqualTo(403);
        assertThat(otherRes.getBody()).contains("FORBIDDEN");

        // Dọn dẹp trạng thái order
        jdbcTemplate.update("UPDATE orders SET status = 'CANCELLED' WHERE id = ?", order.getId());
        jdbcTemplate.update("UPDATE products SET status = 'AVAILABLE' WHERE id = ?", order.getProductId());
    }

    @Test
    @DisplayName("INT-PAY-08 PROCESSING và timeout: (a) PROCESSING -> SUCCESS, (b) timeout 5m -> FAILED (TIMEOUT)")
    void testProcessingAndTimeout() {
        // (a) PROCESSING -> Awaitility -> SUCCESS
        Order orderA = seed.orderPendingPayment(buyer, seller);
        String keyA = "KEY-PROC-" + UUID.randomUUID();

        ResponseEntity<String> resA = api.as(buyer).post(
                "/orders/" + orderA.getId() + "/payments",
                Map.of("method", "MOCK_CARD", "simulateResult", "PROCESSING"),
                Map.of("Idempotency-Key", keyA)
        );
        assertThat(resA.getStatusCode().value()).isEqualTo(202);

        // Chờ callback hoàn tất trong tối đa 5s
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            String pStatus = jdbcTemplate.queryForObject(
                    "SELECT status FROM payments WHERE order_id = ? AND idempotency_key = ?", String.class, orderA.getId(), keyA
            );
            assertThat(pStatus).isEqualTo("SUCCESS");
        });

        String orderAStatus = jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderA.getId());
        assertThat(orderAStatus).isEqualTo("PAID");

        // (b) PROCESSING với callback bị tắt -> timeout 5 phút -> FAILED (TIMEOUT)
        gateway.setDisableDelayedFinalization(true);
        Order orderB = seed.orderPendingPayment(buyer, seller);
        String keyB = "KEY-TIMEOUT-" + UUID.randomUUID();

        ResponseEntity<String> resB = api.as(buyer).post(
                "/orders/" + orderB.getId() + "/payments",
                Map.of("method", "MOCK_CARD", "simulateResult", "PROCESSING"),
                Map.of("Idempotency-Key", keyB)
        );
        assertThat(resB.getStatusCode().value()).isEqualTo(202);

        // Tiến thời gian 5 phút + 1s và tick()
        mutableClock.advance(Duration.ofMinutes(5).plusSeconds(10));
        orderScheduler.tick();

        String pStatusB = jdbcTemplate.queryForObject(
                "SELECT status FROM payments WHERE order_id = ? AND idempotency_key = ?", String.class, orderB.getId(), keyB
        );
        assertThat(pStatusB).isEqualTo("FAILED");
        String failReason = jdbcTemplate.queryForObject(
                "SELECT failure_reason FROM payments WHERE order_id = ? AND idempotency_key = ?", String.class, orderB.getId(), keyB
        );
        assertThat(failReason).contains("TIMEOUT");

        // Thử key mới được phép
        gateway.setDisableDelayedFinalization(false);
        String keyBNew = "KEY-NEW-" + UUID.randomUUID();
        ResponseEntity<String> resBNew = api.as(buyer).post(
                "/orders/" + orderB.getId() + "/payments",
                Map.of("method", "MOCK_CARD", "simulateResult", "SUCCESS"),
                Map.of("Idempotency-Key", keyBNew)
        );
        assertThat(resBNew.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resBNew.getBody()).contains("SUCCESS");
    }
}
