package com.omnibid.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnibid.entity.Order;
import com.omnibid.service.OrderService;
import com.omnibid.support.BaseIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

public class ApiContractIT extends BaseIT {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoSpyBean
    private OrderService orderService;

    @Test
    @DisplayName("INT-API-01 PATCH shipping-info thiếu phoneNumber, shippingAddress 256 ký tự -> 400 VALIDATION_ERROR")
    void testValidationErrorFormat() throws Exception {
        Order order = seed.orderPendingPayment(buyer, seller);

        Map<String, Object> body = Map.of(
                "shippingAddress", "A".repeat(256),
                "phoneNumber", ""
        );

        ResponseEntity<String> res = api.as(buyer).patch("/orders/" + order.getId() + "/shipping-info", body);
        assertThat(res.getStatusCode().value()).isEqualTo(400);

        JsonNode root = objectMapper.readTree(res.getBody());
        assertThat(root.path("success").asBoolean()).isFalse();
        assertThat(root.path("errorCode").asText()).isEqualTo("VALIDATION_ERROR");

        JsonNode data = root.path("data");
        assertThat(data.has("shippingAddress")).isTrue();
        assertThat(data.has("phoneNumber")).isTrue();
    }

    @Test
    @DisplayName("INT-API-02 POST /reports với reportType không tồn tại hoặc JSON hỏng -> 400 VALIDATION_ERROR (không phải 500)")
    void testMalformedJsonAndInvalidEnum() throws Exception {
        // 1. Enum không tồn tại
        Map<String, Object> invalidEnumBody = Map.of(
                "reportType", "INVALID_REPORT_TYPE",
                "reason", "Lý do báo cáo này có trên mười ký tự hợp lệ"
        );
        ResponseEntity<String> res1 = api.as(buyer).post("/reports", invalidEnumBody);
        assertThat(res1.getStatusCode().value()).isEqualTo(400);
        JsonNode root1 = objectMapper.readTree(res1.getBody());
        assertThat(root1.path("success").asBoolean()).isFalse();
        assertThat(root1.path("errorCode").asText()).isEqualTo("VALIDATION_ERROR");

        // 2. JSON hỏng cú pháp
        String malformedJson = "{\"reportType\": ";
        ResponseEntity<String> res2 = api.as(buyer).post("/reports", malformedJson, Map.of("Content-Type", "application/json"));
        assertThat(res2.getStatusCode().value()).isEqualTo(400);
        JsonNode root2 = objectMapper.readTree(res2.getBody());
        assertThat(root2.path("success").asBoolean()).isFalse();
        assertThat(root2.path("errorCode").asText()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    @DisplayName("INT-API-03 Lỗi không lường trước -> 500, errorCode=INTERNAL_ERROR, không lộ stack trace/SQL")
    void testUnexpectedErrorDoesNotLeakInternalDetails() throws Exception {
        doThrow(new NullPointerException("Secret internal NPE database connection failure"))
                .when(orderService).getOrders(any(), any(), any(), any());

        ResponseEntity<String> res = api.as(buyer).get("/orders");
        assertThat(res.getStatusCode().value()).isEqualTo(500);

        JsonNode root = objectMapper.readTree(res.getBody());
        assertThat(root.path("success").asBoolean()).isFalse();
        assertThat(root.path("errorCode").asText()).isEqualTo("INTERNAL_ERROR");

        String body = res.getBody();
        assertThat(body).doesNotContain("NullPointerException");
        assertThat(body).doesNotContain("Secret internal NPE");
        assertThat(body).doesNotContain("com.omnibid");
    }

    @Test
    @DisplayName("INT-API-04 Phân trang GET /orders?page=0&size=2 có đủ content, page, size, totalElements, totalPages")
    void testPaginationStructure() throws Exception {
        seed.orderPendingPayment(buyer, seller);
        seed.orderPendingPayment(buyer, seller);
        seed.orderPendingPayment(buyer, seller);

        ResponseEntity<String> res = api.as(buyer).get("/orders?page=0&size=2");
        assertThat(res.getStatusCode().value()).isEqualTo(200);

        JsonNode root = objectMapper.readTree(res.getBody());
        assertThat(root.path("success").asBoolean()).isTrue();

        JsonNode data = root.path("data");
        assertThat(data.has("content")).isTrue();
        assertThat(data.path("content").isArray()).isTrue();
        assertThat(data.path("content").size()).isEqualTo(2);

        assertThat(data.has("page")).isTrue();
        assertThat(data.path("page").asInt()).isEqualTo(0);

        assertThat(data.has("size")).isTrue();
        assertThat(data.path("size").asInt()).isEqualTo(2);

        assertThat(data.has("totalElements")).isTrue();
        assertThat(data.path("totalElements").asLong()).isGreaterThanOrEqualTo(3L);

        assertThat(data.has("totalPages")).isTrue();
        assertThat(data.path("totalPages").asInt()).isGreaterThanOrEqualTo(2);
    }
}
