package com.omnibid.it;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import com.omnibid.dto.notification.NotificationResponse;
import com.omnibid.dto.websocket.AuctionWebSocketMessage;
import com.omnibid.entity.Auction;
import com.omnibid.entity.Order;
import com.omnibid.entity.Product;
import com.omnibid.entity.Shipment;
import com.omnibid.entity.User;
import com.omnibid.enums.AuctionStatus;
import com.omnibid.enums.NotificationType;
import com.omnibid.enums.ProductStatus;
import com.omnibid.enums.UserRole;
import com.omnibid.enums.UserStatus;
import com.omnibid.repository.AuctionRepository;
import com.omnibid.repository.ShipmentRepository;
import com.omnibid.security.JwtTokenProvider;
import com.omnibid.service.AuctionCloser;
import com.omnibid.service.NotificationService;
import com.omnibid.support.BaseIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class NotificationIntegrationIT extends BaseIT {

    @LocalServerPort
    private int port;

    @Autowired
    private AuctionRepository auctionRepository;

    @Autowired
    private AuctionCloser auctionCloser;

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("INT-NTF-01 Người nhận đúng trong luồng E2E đầy đủ: BUYER, SELLER, BIDDER_B; không gửi nhầm")
    void testCorrectNotificationRecipients() {
        User bidderB = seed.createUser("bidder-b@omnibid.com", "Bidder Bravo", UserRole.USER, UserStatus.ACTIVE);

        LocalDateTime now = LocalDateTime.now(mutableClock);
        Product product = seed.createProduct(seller, category, "Antique Vase", ProductStatus.IN_AUCTION);
        Auction auction = seed.createAuction(seller, product, new BigDecimal("100000"), new BigDecimal("10000"),
                now.minusMinutes(5), now.plusMinutes(30), AuctionStatus.ACTIVE);

        // 1. Buyer đặt giá 110,000
        api.as(buyer).post("/bids", Map.of("auctionId", auction.getId(), "amount", 110000));

        // 2. Bidder B đặt giá 120,000 -> Buyer nhận 1 OUTBID
        api.as(bidderB).post("/bids", Map.of("auctionId", auction.getId(), "amount", 120000));

        // 3. Buyer đặt giá 130,000 -> Bidder B nhận 1 OUTBID
        api.as(buyer).post("/bids", Map.of("auctionId", auction.getId(), "amount", 130000));

        // 4. Hết giờ -> Chốt phiên
        mutableClock.advance(Duration.ofHours(1));
        auctionCloser.closeOne(auction.getId());

        Long orderId = jdbcTemplate.queryForObject(
                "SELECT id FROM orders WHERE auction_id = ?", Long.class, auction.getId()
        );

        // 5. Buyer cập nhật địa chỉ giao hàng
        api.as(buyer).patch("/orders/" + orderId + "/shipping-info", Map.of(
                "shippingAddress", "123 Tran Hung Dao, Q5, TPHCM",
                "phoneNumber", "0901234567"
        ));

        // 6. Buyer thanh toán thất bại
        api.as(buyer).post(
                "/orders/" + orderId + "/payments",
                Map.of("method", "MOCK_CARD", "simulateResult", "FAILED"),
                Map.of("Idempotency-Key", "KEY-FAIL-" + UUID.randomUUID())
        );

        // 7. Buyer thanh toán thành công
        api.as(buyer).post(
                "/orders/" + orderId + "/payments",
                Map.of("method", "MOCK_CARD", "simulateResult", "SUCCESS"),
                Map.of("Idempotency-Key", "KEY-SUCC-" + UUID.randomUUID())
        );

        // 8. Seller cập nhật shipment: PICKED_UP -> IN_TRANSIT -> DELIVERED
        Shipment shipment = shipmentRepository.findByOrderId(orderId).orElseThrow();
        api.as(seller).patch("/shipments/" + shipment.getId() + "/status", Map.of("status", "PICKED_UP", "note", "Đã lấy hàng"));
        api.as(seller).patch("/shipments/" + shipment.getId() + "/status", Map.of("status", "IN_TRANSIT", "note", "Đang vận chuyển"));
        api.as(seller).patch("/shipments/" + shipment.getId() + "/status", Map.of("status", "DELIVERED", "note", "Đã giao thành công"));

        // 9. Buyer xác nhận nhận hàng
        api.as(buyer).patch("/orders/" + orderId + "/confirm-receipt", null);

        // Kiểm tra danh sách thông báo của BUYER
        List<String> buyerNotifs = jdbcTemplate.queryForList(
                "SELECT type FROM notifications WHERE user_id = ? ORDER BY id ASC", String.class, buyer.getId()
        );
        assertThat(buyerNotifs).contains(
                "OUTBID",
                "AUCTION_WON",
                "ORDER_CREATED",
                "PAYMENT_RESULT", // Lần thất bại
                "PAYMENT_RESULT", // Lần thành công
                "SHIPMENT_UPDATE", // PICKED_UP
                "SHIPMENT_UPDATE", // IN_TRANSIT
                "SHIPMENT_UPDATE"  // DELIVERED
        );

        // Kiểm tra danh sách thông báo của SELLER
        List<String> sellerNotifs = jdbcTemplate.queryForList(
                "SELECT type FROM notifications WHERE user_id = ? ORDER BY id ASC", String.class, seller.getId()
        );
        assertThat(sellerNotifs).contains(
                "AUCTION_ENDED",
                "PAYMENT_RESULT",
                "ORDER_UPDATE" // Xác nhận nhận hàng
        );

        // Kiểm tra danh sách thông báo của BIDDER_B: đúng 1 OUTBID
        List<String> bidderBNotifs = jdbcTemplate.queryForList(
                "SELECT type FROM notifications WHERE user_id = ? ORDER BY id ASC", String.class, bidderB.getId()
        );
        assertThat(bidderBNotifs).containsExactly("OUTBID");

        // Người dùng khác (other) không nhận thông báo nào
        Long otherCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE user_id = ?", Long.class, other.getId()
        );
        assertThat(otherCount).isEqualTo(0L);
    }

    @Test
    @DisplayName("INT-NTF-02 Rollback không phát: không lưu DB và không gửi thông báo khi transaction rollback")
    void testRollbackSuppression() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        try {
            tx.execute(status -> {
                notificationService.notify(buyer.getId(), NotificationType.ORDER_UPDATE, "Title Rollback", "Body Rollback", "ORDER", 99999L);
                // Ném exception để trigger rollback
                throw new RuntimeException("Rollback simulation");
            });
        } catch (RuntimeException ignored) {
        }

        Long notifCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE ref_type = 'ORDER' AND ref_id = 99999", Long.class
        );
        assertThat(notifCount).isEqualTo(0L);
    }

    @Test
    @DisplayName("INT-NTF-03 Kênh người dùng WebSocket: STOMP subscribe /user/queue/notifications và /topic/auction/{id}")
    void testWebSocketStompMessaging() throws Exception {
        String buyerToken = jwtTokenProvider.generateToken(buyer.getEmail(), "ROLE_USER");

        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(Jackson2ObjectMapperBuilder.json().build());
        stompClient.setMessageConverter(converter);

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + buyerToken);

        String wsUrl = "ws://localhost:" + port + "/ws-auction";

        CompletableFuture<AuctionWebSocketMessage> auctionTopicFuture = new CompletableFuture<>();
        CompletableFuture<NotificationResponse> userQueueFuture = new CompletableFuture<>();

        StompSession session = stompClient.connectAsync(
                wsUrl,
                new WebSocketHttpHeaders(),
                connectHeaders,
                new StompSessionHandlerAdapter() {}
        ).get(5, TimeUnit.SECONDS);

        // 1. Subscribe topic auction
        Auction auction = seed.createAuction(seller, seed.createProduct(seller, category, "WS Item", ProductStatus.IN_AUCTION),
                new BigDecimal("100000"), new BigDecimal("10000"),
                LocalDateTime.now(mutableClock).minusMinutes(5), LocalDateTime.now(mutableClock).plusMinutes(30),
                AuctionStatus.ACTIVE);

        session.subscribe("/topic/auction/" + auction.getId(), new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return AuctionWebSocketMessage.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                auctionTopicFuture.complete((AuctionWebSocketMessage) payload);
            }
        });

        // 2. Subscribe user notifications queue
        session.subscribe("/user/queue/notifications", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                try {
                    String json = new String((byte[]) payload, java.nio.charset.StandardCharsets.UTF_8);
                    com.fasterxml.jackson.databind.JsonNode node = new ObjectMapper().readTree(json);
                    if ("PAYMENT_RESULT".equals(node.path("type").asText())) {
                        userQueueFuture.complete(NotificationResponse.builder()
                                .type(NotificationType.PAYMENT_RESULT)
                                .title(node.path("title").asText())
                                .build());
                    }
                } catch (Exception e) {
                    userQueueFuture.completeExceptionally(e);
                }
            }
        });

        // Chờ broker đăng ký subscription
        Thread.sleep(500);

        // Đặt giá -> trigger auction topic message
        api.as(buyer).post("/bids", Map.of("auctionId", auction.getId(), "amount", 110000));

        AuctionWebSocketMessage topicMsg = auctionTopicFuture.get(5, TimeUnit.SECONDS);
        assertThat(topicMsg).isNotNull();
        assertThat(topicMsg.getType()).isEqualTo("BID_PLACED");
        assertThat(topicMsg.getAuctionId()).isEqualTo(auction.getId());

        // Gửi thông báo đến buyer trong transaction nghiệp vụ -> trigger user queue notification
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                notificationService.notify(buyer.getId(), NotificationType.PAYMENT_RESULT, "Payment OK", "Paid 110,000", "ORDER", 1L)
        );

        NotificationResponse notifMsg = userQueueFuture.get(5, TimeUnit.SECONDS);
        assertThat(notifMsg).isNotNull();
        assertThat(notifMsg.getType()).isEqualTo(NotificationType.PAYMENT_RESULT);
        assertThat(notifMsg.getTitle()).isEqualTo("Payment OK");

        session.disconnect();
    }
}
