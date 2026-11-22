package com.omnibid.it;

import com.omnibid.entity.Order;
import com.omnibid.support.BaseIT;
import com.omnibid.support.Concurrently;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class PaymentIntegrationIT extends BaseIT {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    @DisplayName("INT-PAY-01 Thanh toán đồng bộ thành công ghi Payment SUCCESS và Order PAID")
    void testPaySuccessWritesPaymentAndUpdatesOrder() {
        Order order = seed.orderPendingPayment(buyer, seller);
        String key = UUID.randomUUID().toString();

        ResponseEntity<String> res = api.as(buyer).post(
                "/orders/" + order.getId() + "/payments",
                Map.of("method", "MOCK_CARD", "simulateResult", "SUCCESS"),
                Map.of("Idempotency-Key", key)
        );

        assertThat(res.getStatusCode().value()).isEqualTo(200);

        List<Map<String, Object>> payments = jdbcTemplate.queryForList(
                "SELECT * FROM payments WHERE order_id = ?", order.getId()
        );
        assertThat(payments).hasSize(1);
        assertThat(payments.get(0).get("status")).isEqualTo("SUCCESS");
        assertThat(payments.get(0).get("attempt_no")).isEqualTo(1);
        assertThat(payments.get(0).get("paid_at")).isNotNull();

        String orderStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM orders WHERE id = ?", String.class, order.getId()
        );
        assertThat(orderStatus).isEqualTo("PAID");
    }

    @Test
    @DisplayName("INT-PAY-02 FAILED rồi SUCCESS khóa mới; gửi lặp khóa cũ trả cùng paymentId")
    void testPayFailedThenSuccessAndIdempotentReplay() {
        Order order = seed.orderPendingPayment(buyer, seller);

        String key1 = UUID.randomUUID().toString();
        ResponseEntity<String> res1 = api.as(buyer).post(
                "/orders/" + order.getId() + "/payments",
                Map.of("method", "MOCK_CARD", "simulateResult", "FAILED"),
                Map.of("Idempotency-Key", key1)
        );
        assertThat(res1.getStatusCode().value()).isEqualTo(200);
        JsonNode data1 = jsonMapper.readTree(res1.getBody()).path("data");
        assertThat(data1.path("status").asText()).isEqualTo("FAILED");
        long failedPaymentId = data1.path("paymentId").asLong();

        // Gửi lặp key1 -> trả lại cùng paymentId
        ResponseEntity<String> replay1 = api.as(buyer).post(
                "/orders/" + order.getId() + "/payments",
                Map.of("method", "MOCK_CARD", "simulateResult", "SUCCESS"),
                Map.of("Idempotency-Key", key1)
        );
        assertThat(replay1.getStatusCode().value()).isEqualTo(200);
        assertThat(jsonMapper.readTree(replay1.getBody()).path("data").path("paymentId").asLong()).isEqualTo(failedPaymentId);

        // Khóa mới key2 -> SUCCESS
        String key2 = UUID.randomUUID().toString();
        ResponseEntity<String> res2 = api.as(buyer).post(
                "/orders/" + order.getId() + "/payments",
                Map.of("method", "MOCK_CARD", "simulateResult", "SUCCESS"),
                Map.of("Idempotency-Key", key2)
        );
        assertThat(res2.getStatusCode().value()).isEqualTo(200);
        JsonNode data2 = jsonMapper.readTree(res2.getBody()).path("data");
        assertThat(data2.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(data2.path("orderStatus").asText()).isEqualTo("PAID");
        long successPaymentId = data2.path("paymentId").asLong();

        // Gửi lặp key2 sau khi PAID -> vẫn trả cùng paymentId
        ResponseEntity<String> replay2 = api.as(buyer).post(
                "/orders/" + order.getId() + "/payments",
                Map.of("method", "MOCK_CARD", "simulateResult", "SUCCESS"),
                Map.of("Idempotency-Key", key2)
        );
        assertThat(replay2.getStatusCode().value()).isEqualTo(200);
        assertThat(jsonMapper.readTree(replay2.getBody()).path("data").path("paymentId").asLong()).isEqualTo(successPaymentId);
    }

    @Test
    @DisplayName("INT-PAY-03 Cùng key, 10 yêu cầu đồng thời chỉ tạo 1 Payment và gọi gateway đúng 1 lần")
    void testSameKeyConcurrentTenRequests() throws Exception {
        Order order = seed.orderPendingPayment(buyer, seller);
        String key = UUID.randomUUID().toString();

        List<ResponseEntity<String>> results = Concurrently.run(10, () ->
                api.as(buyer).post(
                        "/orders/" + order.getId() + "/payments",
                        Map.of("method", "MOCK_CARD", "simulateResult", "SUCCESS"),
                        Map.of("Idempotency-Key", key)
                )
        );

        assertThat(results).allSatisfy(r -> assertThat(r.getStatusCode().value()).isEqualTo(200));

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payments WHERE order_id = ?", Long.class, order.getId()
        );
        assertThat(count).isEqualTo(1L);
        assertThat(gateway.chargeCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("INT-PAY-04 20 luồng key khác nhau đồng thời: đúng 1 Payment SUCCESS, còn lại 409")
    void testTwentyConcurrentDifferentKeysOnlyOneSuccess() throws Exception {
        Order order = seed.orderPendingPayment(buyer, seller);

        List<ResponseEntity<String>> results = Concurrently.runIndexed(20, index ->
                api.as(buyer).post(
                        "/orders/" + order.getId() + "/payments",
                        Map.of("method", "MOCK_CARD", "simulateResult", "SUCCESS"),
                        Map.of("Idempotency-Key", UUID.randomUUID().toString())
                )
        );

        long successPayments = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payments WHERE order_id = ? AND status = 'SUCCESS'", Long.class, order.getId()
        );
        assertThat(successPayments).isEqualTo(1L);

        long okCount = results.stream().filter(r -> r.getStatusCode().value() == 200).count();
        long conflictCount = results.stream().filter(r -> r.getStatusCode().value() == 409).count();
        assertThat(okCount).isEqualTo(1L);
        assertThat(conflictCount).isEqualTo(19L);
    }

    @Test
    @DisplayName("INT-PAY-05 Quá hạn thanh toán -> 409 ORDER_EXPIRED")
    void testExpiredOrderReturnsOrderExpired() {
        Order order = seed.orderPendingPayment(buyer, seller);
        mutableClock.advance(Duration.ofHours(48).plusSeconds(1));

        ResponseEntity<String> res = api.as(buyer).post(
                "/orders/" + order.getId() + "/payments",
                Map.of("method", "MOCK_CARD", "simulateResult", "SUCCESS"),
                Map.of("Idempotency-Key", UUID.randomUUID().toString())
        );

        assertThat(res.getStatusCode().value()).isEqualTo(409);
        assertThat(res.getBody()).contains("ORDER_EXPIRED");
    }
}
