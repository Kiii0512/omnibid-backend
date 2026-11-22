package com.omnibid.it;

import com.omnibid.entity.Auction;
import com.omnibid.entity.Product;
import com.omnibid.entity.User;
import com.omnibid.enums.AuctionStatus;
import com.omnibid.enums.NotificationType;
import com.omnibid.enums.ProductStatus;
import com.omnibid.enums.UserRole;
import com.omnibid.enums.UserStatus;
import com.omnibid.security.JwtTokenProvider;
import com.omnibid.service.AuctionCloser;
import com.omnibid.service.NotificationService;
import com.omnibid.support.BaseIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class NotificationIntegrationIT extends BaseIT {

    @Autowired
    private AuctionCloser auctionCloser;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    @DisplayName("INT-NTF-01 Người nhận đúng trong luồng E2E v4 (5 NotificationType)")
    void testCorrectNotificationRecipients() {
        User secondBidder = seed.createUser("bidder-b2@omnibid.com", "Bidder Bravo", UserRole.USER, UserStatus.ACTIVE);

        Instant now = mutableClock.instant();
        Product product = seed.createProduct(seller, category, "Antique Vase", ProductStatus.IN_AUCTION);
        Auction auction = seed.createAuction(seller, product, new BigDecimal("100000"), new BigDecimal("10000"),
                now.minus(5, ChronoUnit.MINUTES), now.plus(30, ChronoUnit.MINUTES), AuctionStatus.ACTIVE);

        api.as(buyer).post("/auctions/" + auction.getId() + "/bids", Map.of("amount", 110000));
        api.as(secondBidder).post("/auctions/" + auction.getId() + "/bids", Map.of("amount", 120000));
        api.as(buyer).post("/auctions/" + auction.getId() + "/bids", Map.of("amount", 130000));

        mutableClock.advance(Duration.ofHours(1));
        auctionCloser.closeOne(auction.getId());

        Long orderId = jdbcTemplate.queryForObject(
                "SELECT id FROM orders WHERE auction_id = ?", Long.class, auction.getId()
        );

        api.as(buyer).patch("/orders/" + orderId + "/shipping-info", Map.of(
                "shippingAddress", "123 Tran Hung Dao, Q5, TPHCM",
                "phoneNumber", "0901234567"
        ));

        api.as(buyer).post(
                "/orders/" + orderId + "/payments",
                Map.of("method", "MOCK_CARD", "simulateResult", "SUCCESS"),
                Map.of("Idempotency-Key", UUID.randomUUID().toString())
        );

        api.as(seller).post("/orders/" + orderId + "/ship", Map.of("trackingCode", "VN999999"));
        api.as(buyer).post("/orders/" + orderId + "/confirm-receipt", null);

        List<String> buyerNotifs = jdbcTemplate.queryForList(
                "SELECT type FROM notifications WHERE user_id = ? ORDER BY id ASC", String.class, buyer.getId()
        );
        assertThat(buyerNotifs).contains("OUTBID", "AUCTION_RESULT", "ORDER_UPDATE");

        List<String> sellerNotifs = jdbcTemplate.queryForList(
                "SELECT type FROM notifications WHERE user_id = ? ORDER BY id ASC", String.class, seller.getId()
        );
        assertThat(sellerNotifs).contains("AUCTION_RESULT", "ORDER_UPDATE");

        List<String> bidderBNotifs = jdbcTemplate.queryForList(
                "SELECT type FROM notifications WHERE user_id = ? ORDER BY id ASC", String.class, secondBidder.getId()
        );
        assertThat(bidderBNotifs).containsExactly("OUTBID");

        Long otherCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE user_id = ?", Long.class, other.getId()
        );
        assertThat(otherCount).isEqualTo(0L);
    }

    @Test
    @DisplayName("INT-NTF-02 Rollback không phát: không lưu DB khi transaction rollback")
    void testRollbackSuppression() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        try {
            tx.execute(status -> {
                notificationService.notify(buyer.getId(), NotificationType.ORDER_UPDATE, "Title Rollback", "Body Rollback", "ORDER", 99999L);
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
    @DisplayName("INT-NTF-03 WebSocket STOMP /ws subscribe /topic/auctions/{id} nhận AuctionEvent BID_PLACED")
    void testWebSocketStompAuctionTopic() throws Exception {
        String buyerToken = jwtTokenProvider.generateToken(buyer.getId(), buyer.getRole(), buyer.getTokenVersion());

        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MessageConverter() {
            @Override
            public Object fromMessage(Message<?> message, Class<?> targetClass) {
                Object payload = message.getPayload();
                if (payload instanceof byte[] bytes) {
                    return new String(bytes, StandardCharsets.UTF_8);
                }
                return String.valueOf(payload);
            }

            @Override
            public Message<?> toMessage(Object payload, MessageHeaders headers) {
                return null;
            }
        });

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + buyerToken);

        String wsUrl = "ws://localhost:" + port + "/ws";

        CompletableFuture<JsonNode> auctionTopicFuture = new CompletableFuture<>();

        WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
        handshakeHeaders.setOrigin("http://localhost:3000");

        StompSession session = stompClient.connectAsync(
                wsUrl,
                handshakeHeaders,
                connectHeaders,
                new StompSessionHandlerAdapter() {}
        ).get(5, TimeUnit.SECONDS);

        Instant now = mutableClock.instant();
        Auction auction = seed.createAuction(seller, seed.createProduct(seller, category, "WS Item", ProductStatus.IN_AUCTION),
                new BigDecimal("100000"), new BigDecimal("10000"),
                now.minus(5, ChronoUnit.MINUTES), now.plus(30, ChronoUnit.MINUTES),
                AuctionStatus.ACTIVE);

        session.subscribe("/topic/auctions/" + auction.getId(), new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                try {
                    auctionTopicFuture.complete(jsonMapper.readTree((String) payload));
                } catch (Exception e) {
                    auctionTopicFuture.completeExceptionally(e);
                }
            }
        });

        Thread.sleep(300);

        var bidRes = api.as(buyer).post("/auctions/" + auction.getId() + "/bids", Map.of("amount", 110000));
        assertThat(bidRes.getStatusCode().value()).isEqualTo(201);

        JsonNode event = auctionTopicFuture.get(5, TimeUnit.SECONDS);
        assertThat(event.path("type").asText()).isEqualTo("BID_PLACED");
        assertThat(event.path("auctionId").asLong()).isEqualTo(auction.getId());
        assertThat(event.path("currentPrice").asLong()).isEqualTo(110000L);

        session.disconnect();
    }
}
