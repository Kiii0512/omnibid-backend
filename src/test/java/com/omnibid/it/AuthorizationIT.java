package com.omnibid.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnibid.entity.Order;
import com.omnibid.entity.Shipment;
import com.omnibid.entity.User;
import com.omnibid.enums.UserRole;
import com.omnibid.enums.UserStatus;
import com.omnibid.repository.ShipmentRepository;
import com.omnibid.repository.UserRepository;
import com.omnibid.security.JwtTokenProvider;
import com.omnibid.service.NotificationService;
import com.omnibid.enums.NotificationType;
import com.omnibid.support.BaseIT;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class AuthorizationIT extends BaseIT {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Test
    @DisplayName("INT-AUT-01 Không token / token sai / token hết hạn gọi GET /orders -> 401 UNAUTHORIZED")
    void testUnauthenticatedRequests() {
        // 1. Không token
        ResponseEntity<String> noTokenRes = api.anonymous().get("/orders");
        assertThat(noTokenRes.getStatusCode().value()).isEqualTo(401);
        assertThat(noTokenRes.getBody()).contains("UNAUTHORIZED");

        // 2. Token sai định dạng / sai chữ ký
        ResponseEntity<String> badTokenRes = api.asToken("invalid.bearer.token").get("/orders");
        assertThat(badTokenRes.getStatusCode().value()).isEqualTo(401);
        assertThat(badTokenRes.getBody()).contains("UNAUTHORIZED");

        // 3. Token hết hạn
        Key key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        Date past = new Date(now.getTime() - 3600_000); // hết hạn 1 tiếng trước
        String expiredToken = Jwts.builder()
                .setSubject(buyer.getEmail())
                .claim("role", "ROLE_USER")
                .setIssuedAt(new Date(now.getTime() - 7200_000))
                .setExpiration(past)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        ResponseEntity<String> expiredRes = api.asToken(expiredToken).get("/orders");
        assertThat(expiredRes.getStatusCode().value()).isEqualTo(401);
        assertThat(expiredRes.getBody()).contains("UNAUTHORIZED");
    }

    @Test
    @DisplayName("INT-AUT-02 GET /orders/{id} và /shipment bởi BUYER, SELLER, ADMIN, OTHER")
    void testOrderAndShipmentAccessPermissions() {
        Order order = seed.orderPaid(buyer, seller);
        Shipment shipment = shipmentRepository.findByOrderId(order.getId()).orElseThrow();

        // BUYER: 200, 200
        ResponseEntity<String> buyerOrderRes = api.as(buyer).get("/orders/" + order.getId());
        assertThat(buyerOrderRes.getStatusCode().value()).isEqualTo(200);
        ResponseEntity<String> buyerShipmentRes = api.as(buyer).get("/orders/" + order.getId() + "/shipment");
        assertThat(buyerShipmentRes.getStatusCode().value()).isEqualTo(200);

        // SELLER: 200, 200
        ResponseEntity<String> sellerOrderRes = api.as(seller).get("/orders/" + order.getId());
        assertThat(sellerOrderRes.getStatusCode().value()).isEqualTo(200);
        ResponseEntity<String> sellerShipmentRes = api.as(seller).get("/orders/" + order.getId() + "/shipment");
        assertThat(sellerShipmentRes.getStatusCode().value()).isEqualTo(200);

        // ADMIN: 200, 200
        ResponseEntity<String> adminOrderRes = api.as(admin).get("/orders/" + order.getId());
        assertThat(adminOrderRes.getStatusCode().value()).isEqualTo(200);
        ResponseEntity<String> adminShipmentRes = api.as(admin).get("/orders/" + order.getId() + "/shipment");
        assertThat(adminShipmentRes.getStatusCode().value()).isEqualTo(200);

        // OTHER: 403, 403
        ResponseEntity<String> otherOrderRes = api.as(other).get("/orders/" + order.getId());
        assertThat(otherOrderRes.getStatusCode().value()).isEqualTo(403);
        ResponseEntity<String> otherShipmentRes = api.as(other).get("/orders/" + order.getId() + "/shipment");
        assertThat(otherShipmentRes.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    @DisplayName("INT-AUT-03 GET /orders của OTHER khi hệ thống có đơn của người khác: không thấy đơn của họ")
    void testListOrdersIsolation() throws Exception {
        seed.orderPendingPayment(buyer, seller);

        ResponseEntity<String> res = api.as(other).get("/orders");
        assertThat(res.getStatusCode().value()).isEqualTo(200);

        JsonNode root = objectMapper.readTree(res.getBody());
        JsonNode content = root.path("data").path("content");
        assertThat(content.isArray()).isTrue();
        assertThat(content.size()).isEqualTo(0);
    }

    @Test
    @DisplayName("INT-AUT-04 POST /orders/{id}/payments bởi SELLER, ADMIN, OTHER; GET .../payments bởi SELLER")
    void testPaymentAccessPermissions() {
        Order order = seed.orderPendingPayment(buyer, seller);
        Map<String, Object> body = Map.of("method", "MOCK_CARD");
        Map<String, String> headers = Map.of("Idempotency-Key", "KEY-" + UUID.randomUUID());

        // SELLER gọi POST: 403
        ResponseEntity<String> sPostRes = api.as(seller).post("/orders/" + order.getId() + "/payments", body, headers);
        assertThat(sPostRes.getStatusCode().value()).isEqualTo(403);

        // ADMIN gọi POST: 403
        ResponseEntity<String> aPostRes = api.as(admin).post("/orders/" + order.getId() + "/payments", body, headers);
        assertThat(aPostRes.getStatusCode().value()).isEqualTo(403);

        // OTHER gọi POST: 403
        ResponseEntity<String> oPostRes = api.as(other).post("/orders/" + order.getId() + "/payments", body, headers);
        assertThat(oPostRes.getStatusCode().value()).isEqualTo(403);

        // SELLER gọi GET /payments: 403
        ResponseEntity<String> sGetRes = api.as(seller).get("/orders/" + order.getId() + "/payments");
        assertThat(sGetRes.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    @DisplayName("INT-AUT-05 User bị khóa dùng token cũ gọi GET /orders, POST /bids, POST /payments -> 403 ACCOUNT_BANNED")
    void testBannedUserBlocked() {
        User bannedUser = seed.createUser("banned-user@omnibid.com", "Banned Guy", UserRole.USER, UserStatus.ACTIVE);
        String token = jwtTokenProvider.generateToken(bannedUser.getEmail(), "ROLE_USER");

        // Cập nhật trạng thái người dùng sang BANNED
        bannedUser.setStatus(UserStatus.BANNED);
        userRepository.save(bannedUser);

        Order order = seed.orderPendingPayment(buyer, seller);

        // 1. GET /orders
        ResponseEntity<String> getRes = api.asToken(token).get("/orders");
        assertThat(getRes.getStatusCode().value()).isEqualTo(403);
        assertThat(getRes.getBody()).contains("ACCOUNT_BANNED");

        // 2. POST /bids
        ResponseEntity<String> bidRes = api.asToken(token).post("/bids", Map.of("auctionId", 1L, "amount", 100000));
        assertThat(bidRes.getStatusCode().value()).isEqualTo(403);
        assertThat(bidRes.getBody()).contains("ACCOUNT_BANNED");

        // 3. POST /orders/{id}/payments
        ResponseEntity<String> payRes = api.asToken(token).post(
                "/orders/" + order.getId() + "/payments",
                Map.of("method", "MOCK_CARD"),
                Map.of("Idempotency-Key", "KEY-" + UUID.randomUUID())
        );
        assertThat(payRes.getStatusCode().value()).isEqualTo(403);
        assertThat(payRes.getBody()).contains("ACCOUNT_BANNED");
    }

    @Test
    @DisplayName("INT-AUT-06 USER gọi các endpoint admin -> 403 FORBIDDEN")
    void testAdminEndpointsProtected() {
        ResponseEntity<String> res1 = api.as(buyer).get("/admin/users");
        assertThat(res1.getStatusCode().value()).isEqualTo(403);

        ResponseEntity<String> res2 = api.as(buyer).get("/admin/statistics");
        assertThat(res2.getStatusCode().value()).isEqualTo(403);

        ResponseEntity<String> res3 = api.as(buyer).patch("/admin/reports/1/resolve", Map.of("action", "NONE"));
        assertThat(res3.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    @DisplayName("INT-AUT-07 PATCH /shipments/{id}/status bởi BUYER, OTHER, SELLER của đơn khác -> 403")
    void testShipmentStatusUpdatePermissions() {
        Order order = seed.orderPaid(buyer, seller);
        Shipment shipment = shipmentRepository.findByOrderId(order.getId()).orElseThrow();
        Map<String, Object> body = Map.of("status", "PICKED_UP", "note", "Đã lấy hàng");

        // BUYER: 403
        ResponseEntity<String> buyerRes = api.as(buyer).patch("/shipments/" + shipment.getId() + "/status", body);
        assertThat(buyerRes.getStatusCode().value()).isEqualTo(403);

        // OTHER: 403
        ResponseEntity<String> otherRes = api.as(other).patch("/shipments/" + shipment.getId() + "/status", body);
        assertThat(otherRes.getStatusCode().value()).isEqualTo(403);

        // SELLER của đơn khác: 403
        User anotherSeller = seed.createUser("seller2@omnibid.com", "Seller Two", UserRole.USER, UserStatus.ACTIVE);
        ResponseEntity<String> anotherSellerRes = api.as(anotherSeller).patch("/shipments/" + shipment.getId() + "/status", body);
        assertThat(anotherSellerRes.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    @DisplayName("INT-AUT-08 OTHER PATCH /notifications/{id}/read thông báo của BUYER -> 404, không đổi is_read")
    void testNotificationReadPermissions() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                notificationService.notify(buyer.getId(), NotificationType.ORDER_CREATED, "Test Title", "Test Content", "ORDER", 999L)
        );
        Long notifId = jdbcTemplate.queryForObject(
                "SELECT id FROM notifications WHERE user_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, buyer.getId()
        );

        ResponseEntity<String> res = api.as(other).patch("/notifications/" + notifId + "/read", null);
        assertThat(res.getStatusCode().value()).isEqualTo(404);

        Boolean isRead = jdbcTemplate.queryForObject(
                "SELECT is_read FROM notifications WHERE id = ?", Boolean.class, notifId
        );
        assertThat(isRead).isFalse();
    }

    @Test
    @DisplayName("INT-AUT-09 COMPLAINT report bởi SELLER / OTHER -> 403")
    void testComplaintReportPermissions() {
        Order order = seed.orderPaid(buyer, seller);
        Map<String, Object> body = Map.of(
                "reportType", "COMPLAINT",
                "orderId", order.getId(),
                "reason", "Hàng bị móp méo hộp nghiêm trọng khi vận chuyển"
        );

        // SELLER tạo COMPLAINT: 403
        ResponseEntity<String> sellerRes = api.as(seller).post("/reports", body);
        assertThat(sellerRes.getStatusCode().value()).isEqualTo(403);

        // OTHER tạo COMPLAINT: 403
        ResponseEntity<String> otherRes = api.as(other).post("/reports", body);
        assertThat(otherRes.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    @DisplayName("INT-AUT-10 Preflight CORS từ origin Frontend với header Authorization, Idempotency-Key (G-18)")
    void testCorsPreflight() {
        Map<String, String> headers = Map.of(
                "Origin", "http://localhost:3000",
                "Access-Control-Request-Method", "POST",
                "Access-Control-Request-Headers", "authorization,idempotency-key"
        );

        ResponseEntity<String> res = api.anonymous().options("/orders/1/payments", headers);
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();

        assertThat(res.getHeaders().getAccessControlAllowOrigin()).isEqualTo("http://localhost:3000");
        assertThat(res.getHeaders().getAccessControlExposeHeaders()).contains("Retry-After");
    }
}
