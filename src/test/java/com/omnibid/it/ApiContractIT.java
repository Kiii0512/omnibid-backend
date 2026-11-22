package com.omnibid.it;

import com.omnibid.entity.Order;
import com.omnibid.service.OrderService;
import com.omnibid.support.BaseIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

public class ApiContractIT extends BaseIT {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @MockitoSpyBean
    private OrderService orderService;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    @DisplayName("INT-API-00 Hợp đồng REST v4: đúng 54 endpoint dưới /api/v1/**")
    void testExact54RestEndpoints() {
        Set<String> endpoints = new HashSet<>();
        for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
            Set<String> patterns = info.getPatternValues();
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            for (String pattern : patterns) {
                if (pattern.startsWith("/api/v1")) {
                    for (RequestMethod method : methods) {
                        endpoints.add(method.name() + " " + pattern);
                    }
                }
            }
        }
        assertThat(endpoints)
                .as("Số lượng endpoint /api/v1/** phải đúng 54 theo API_SPEC_v4.md")
                .hasSize(54);
    }

    @Test
    @DisplayName("INT-API-01 PATCH shipping-info thiếu phoneNumber, shippingAddress 256 ký tự -> 400 VALIDATION_ERROR")
    void testValidationErrorFormat() {
        Order order = seed.orderPendingPayment(buyer, seller);

        Map<String, Object> body = Map.of(
                "shippingAddress", "A".repeat(256),
                "phoneNumber", ""
        );

        ResponseEntity<String> res = api.as(buyer).patch("/orders/" + order.getId() + "/shipping-info", body);
        assertThat(res.getStatusCode().value()).isEqualTo(400);

        JsonNode root = jsonMapper.readTree(res.getBody());
        assertThat(root.path("success").asBoolean()).isFalse();
        assertThat(root.path("error").path("code").asText()).isEqualTo("VALIDATION_ERROR");

        JsonNode fields = root.path("error").path("details").path("fields");
        assertThat(fields.has("shippingAddress")).isTrue();
        assertThat(fields.has("phoneNumber")).isTrue();
    }

    @Test
    @DisplayName("INT-API-02 JSON hỏng cú pháp hoặc body không hợp lệ -> 400 VALIDATION_ERROR")
    void testMalformedJsonReturns400() {
        String malformedJson = "{\"reportedProductId\": ";
        ResponseEntity<String> res = api.as(buyer).post("/reports", malformedJson, Map.of("Content-Type", "application/json"));
        assertThat(res.getStatusCode().value()).isEqualTo(400);

        JsonNode root = jsonMapper.readTree(res.getBody());
        assertThat(root.path("success").asBoolean()).isFalse();
        assertThat(root.path("error").path("code").asText()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    @DisplayName("INT-API-03 Lỗi không lường trước -> 500 INTERNAL_ERROR, không lộ stack trace")
    void testUnexpectedErrorDoesNotLeakInternalDetails() {
        doThrow(new NullPointerException("Secret internal NPE database connection failure"))
                .when(orderService).getOrders(any(), any(), any(), any());

        ResponseEntity<String> res = api.as(buyer).get("/orders");
        assertThat(res.getStatusCode().value()).isEqualTo(500);

        JsonNode root = jsonMapper.readTree(res.getBody());
        assertThat(root.path("success").asBoolean()).isFalse();
        assertThat(root.path("error").path("code").asText()).isEqualTo("INTERNAL_ERROR");

        String body = res.getBody();
        assertThat(body).doesNotContain("NullPointerException");
        assertThat(body).doesNotContain("Secret internal NPE");
        assertThat(body).doesNotContain("com.omnibid");
    }

    @Test
    @DisplayName("INT-API-04 Phân trang GET /orders + định dạng JSON thời gian +07:00 và tiền số nguyên")
    void testPaginationAndJsonFormat() {
        seed.orderPendingPayment(buyer, seller);
        seed.orderPendingPayment(buyer, seller);
        seed.orderPendingPayment(buyer, seller);

        ResponseEntity<String> res = api.as(buyer).get("/orders?page=0&size=2");
        assertThat(res.getStatusCode().value()).isEqualTo(200);

        JsonNode root = jsonMapper.readTree(res.getBody());
        assertThat(root.path("success").asBoolean()).isTrue();

        JsonNode data = root.path("data");
        assertThat(data.path("content").size()).isEqualTo(2);
        assertThat(data.path("page").asInt()).isEqualTo(0);
        assertThat(data.path("size").asInt()).isEqualTo(2);
        assertThat(data.path("totalElements").asLong()).isGreaterThanOrEqualTo(3L);
        assertThat(data.path("totalPages").asInt()).isGreaterThanOrEqualTo(2);

        JsonNode first = data.path("content").get(0);
        assertThat(first.path("createdAt").asText()).endsWith("+07:00");
        assertThat(first.path("finalPrice").isIntegralNumber()).isTrue();
    }
}
