package com.omnibid.it;

import com.omnibid.entity.Order;
import com.omnibid.entity.User;
import com.omnibid.enums.NotificationType;
import com.omnibid.enums.UserRole;
import com.omnibid.enums.UserStatus;
import com.omnibid.repository.UserRepository;
import com.omnibid.security.JwtTokenProvider;
import com.omnibid.service.NotificationService;
import com.omnibid.support.BaseIT;
import com.omnibid.support.Seed;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Duration;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class AuthorizationIT extends BaseIT {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Test
    @DisplayName("INT-AUT-01 Không token / token sai / token hết hạn gọi GET /orders -> 401 UNAUTHORIZED")
    void testUnauthenticatedRequests() {
        ResponseEntity<String> noTokenRes = api.anonymous().get("/orders");
        assertThat(noTokenRes.getStatusCode().value()).isEqualTo(401);
        assertThat(noTokenRes.getBody()).contains("UNAUTHORIZED");

        ResponseEntity<String> badTokenRes = api.asToken("invalid.bearer.token").get("/orders");
        assertThat(badTokenRes.getStatusCode().value()).isEqualTo(401);
        assertThat(badTokenRes.getBody()).contains("UNAUTHORIZED");

        Key key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Date now = Date.from(mutableClock.instant());
        Date past = new Date(now.getTime() - 3600_000);
        String expiredToken = Jwts.builder()
                .setSubject(String.valueOf(buyer.getId()))
                .claim("role", "USER")
                .claim("ver", buyer.getTokenVersion())
                .setIssuedAt(new Date(now.getTime() - 7200_000))
                .setExpiration(past)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        ResponseEntity<String> expiredRes = api.asToken(expiredToken).get("/orders");
        assertThat(expiredRes.getStatusCode().value()).isEqualTo(401);
        assertThat(expiredRes.getBody()).contains("UNAUTHORIZED");
    }

    @Test
    @DisplayName("INT-AUT-01b Đăng xuất và đổi mật khẩu vô hiệu hóa JWT cũ (401 UNAUTHORIZED)")
    void testLogoutAndChangePasswordInvalidateOldToken() {
        String token1 = jwtTokenProvider.generateToken(buyer.getId(), buyer.getRole(), buyer.getTokenVersion());
        assertThat(api.asToken(token1).get("/users/me").getStatusCode().value()).isEqualTo(200);

        // Đăng xuất -> token1 thành 401
        ResponseEntity<String> logoutRes = api.asToken(token1).post("/auth/logout", null);
        assertThat(logoutRes.getStatusCode().value()).isEqualTo(200);
        assertThat(api.asToken(token1).get("/users/me").getStatusCode().value()).isEqualTo(401);

        // Đăng nhập lại để lấy token mới
        ResponseEntity<String> loginRes = api.anonymous().post("/auth/login", Map.of(
                "email", buyer.getEmail(),
                "password", Seed.DEFAULT_PASSWORD
        ));
        assertThat(loginRes.getStatusCode().value()).isEqualTo(200);
        String token2 = jsonMapper.readTree(loginRes.getBody()).path("data").path("accessToken").asText();

        // Đổi mật khẩu -> token2 thành 401
        ResponseEntity<String> changePwRes = api.asToken(token2).patch("/users/me/password", Map.of(
                "currentPassword", Seed.DEFAULT_PASSWORD,
                "newPassword", "NewPassword456",
                "confirmPassword", "NewPassword456"
        ));
        assertThat(changePwRes.getStatusCode().value()).isEqualTo(200);
        assertThat(api.asToken(token2).get("/users/me").getStatusCode().value()).isEqualTo(401);
    }

    @Test
    @DisplayName("INT-AUT-01c 5 lần đăng nhập sai -> lần 6 trả 429 kể cả đúng mật khẩu; sau 15 phút đăng nhập lại được")
    void testLoginRateLimitAndRecoveryAfter15Minutes() {
        for (int i = 0; i < 5; i++) {
            ResponseEntity<String> fail = api.anonymous().post("/auth/login", Map.of(
                    "email", buyer.getEmail(),
                    "password", "WrongPass123"
            ));
            assertThat(fail.getStatusCode().value()).isEqualTo(401);
        }

        // Lần 6 kể cả đúng mật khẩu -> 429
        ResponseEntity<String> blocked = api.anonymous().post("/auth/login", Map.of(
                "email", buyer.getEmail(),
                "password", Seed.DEFAULT_PASSWORD
        ));
        assertThat(blocked.getStatusCode().value()).isEqualTo(429);
        assertThat(blocked.getBody()).contains("TOO_MANY_REQUESTS");

        // Sau 15 phút -> đăng nhập thành công
        mutableClock.advance(Duration.ofMinutes(15).plusSeconds(1));
        ResponseEntity<String> ok = api.anonymous().post("/auth/login", Map.of(
                "email", buyer.getEmail(),
                "password", Seed.DEFAULT_PASSWORD
        ));
        assertThat(ok.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    @DisplayName("INT-AUT-02 GET /orders/{id} bởi BUYER, SELLER (200), ADMIN (403), OTHER (403)")
    void testOrderAccessPermissions() {
        Order order = seed.orderPaid(buyer, seller);

        assertThat(api.as(buyer).get("/orders/" + order.getId()).getStatusCode().value()).isEqualTo(200);
        assertThat(api.as(seller).get("/orders/" + order.getId()).getStatusCode().value()).isEqualTo(200);
        assertThat(api.as(admin).get("/orders/" + order.getId()).getStatusCode().value()).isEqualTo(403);
        assertThat(api.as(other).get("/orders/" + order.getId()).getStatusCode().value()).isEqualTo(403);
        // Admin xem qua /admin/orders/{id} -> 200
        assertThat(api.as(admin).get("/admin/orders/" + order.getId()).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    @DisplayName("INT-AUT-03 GET /orders của OTHER không thấy đơn người khác")
    void testListOrdersIsolation() {
        seed.orderPendingPayment(buyer, seller);

        ResponseEntity<String> res = api.as(other).get("/orders");
        assertThat(res.getStatusCode().value()).isEqualTo(200);

        JsonNode content = jsonMapper.readTree(res.getBody()).path("data").path("content");
        assertThat(content.isArray()).isTrue();
        assertThat(content.size()).isEqualTo(0);
    }

    @Test
    @DisplayName("INT-AUT-04 POST/GET /orders/{id}/payments bởi SELLER, ADMIN, OTHER -> 403")
    void testPaymentAccessPermissions() {
        Order order = seed.orderPendingPayment(buyer, seller);
        Map<String, Object> body = Map.of("method", "MOCK_CARD");
        Map<String, String> headers = Map.of("Idempotency-Key", UUID.randomUUID().toString());

        assertThat(api.as(seller).post("/orders/" + order.getId() + "/payments", body, headers).getStatusCode().value()).isEqualTo(403);
        assertThat(api.as(admin).post("/orders/" + order.getId() + "/payments", body, headers).getStatusCode().value()).isEqualTo(403);
        assertThat(api.as(other).post("/orders/" + order.getId() + "/payments", body, headers).getStatusCode().value()).isEqualTo(403);
        assertThat(api.as(seller).get("/orders/" + order.getId() + "/payments").getStatusCode().value()).isEqualTo(403);
    }

    @Test
    @DisplayName("INT-AUT-05 User BANNED với token còn hạn -> 403 ACCOUNT_BANNED ở cả endpoint bảo vệ và Public")
    void testBannedUserBlockedEverywhere() {
        User bannedUser = seed.createUser("banned-user@omnibid.com", "Banned Guy", UserRole.USER, UserStatus.ACTIVE);
        String token = jwtTokenProvider.generateToken(bannedUser.getId(), UserRole.USER, bannedUser.getTokenVersion());

        bannedUser.setStatus(UserStatus.BANNED);
        userRepository.save(bannedUser);

        // Endpoint bảo vệ
        ResponseEntity<String> protectedRes = api.asToken(token).get("/orders");
        assertThat(protectedRes.getStatusCode().value()).isEqualTo(403);
        assertThat(protectedRes.getBody()).contains("ACCOUNT_BANNED");

        // Endpoint Public
        ResponseEntity<String> publicRes = api.asToken(token).get("/auctions");
        assertThat(publicRes.getStatusCode().value()).isEqualTo(403);
        assertThat(publicRes.getBody()).contains("ACCOUNT_BANNED");
    }

    @Test
    @DisplayName("INT-AUT-06 USER gọi các endpoint admin -> 403 FORBIDDEN")
    void testAdminEndpointsProtected() {
        assertThat(api.as(buyer).get("/admin/users").getStatusCode().value()).isEqualTo(403);
        assertThat(api.as(buyer).get("/admin/statistics").getStatusCode().value()).isEqualTo(403);
        assertThat(api.as(buyer).post("/admin/reports/1/resolve", Map.of("decision", "REJECTED", "note", "x")).getStatusCode().value()).isEqualTo(403);
    }

    @Test
    @DisplayName("INT-AUT-08 OTHER POST /notifications/{id}/read thông báo của BUYER -> 403 FORBIDDEN, không đổi is_read")
    void testNotificationReadPermissions() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                notificationService.notify(buyer.getId(), NotificationType.ORDER_UPDATE, "Test Title", "Test Content", "ORDER", 999L)
        );
        Long notifId = jdbcTemplate.queryForObject(
                "SELECT id FROM notifications WHERE user_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, buyer.getId()
        );

        ResponseEntity<String> res = api.as(other).post("/notifications/" + notifId + "/read", null);
        assertThat(res.getStatusCode().value()).isEqualTo(403);

        Boolean isRead = jdbcTemplate.queryForObject(
                "SELECT is_read FROM notifications WHERE id = ?", Boolean.class, notifId
        );
        assertThat(isRead).isFalse();
    }

    @Test
    @DisplayName("INT-AUT-10 Preflight CORS từ origin Frontend với header Authorization, Idempotency-Key")
    void testCorsPreflight() {
        Map<String, String> headers = Map.of(
                "Origin", "http://localhost:3000",
                "Access-Control-Request-Method", "POST",
                "Access-Control-Request-Headers", "authorization,idempotency-key"
        );

        ResponseEntity<String> res = api.anonymous().options("/orders/1/payments", headers);
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(res.getHeaders().getAccessControlAllowOrigin()).isEqualTo("http://localhost:3000");
    }
}
