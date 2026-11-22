package com.omnibid.service;

import com.omnibid.common.AfterCommitExecutor;
import com.omnibid.dto.bid.BidHistoryItem;
import com.omnibid.dto.websocket.AuctionEvent;
import com.omnibid.entity.User;
import com.omnibid.enums.AuctionEventType;
import com.omnibid.enums.AuctionStatus;
import com.omnibid.enums.ReserveStatus;
import com.omnibid.repository.UserRepository;
import com.omnibid.service.impl.WebSocketPublisherImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSocketPublisherTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private UserRepository userRepository;

    private WebSocketPublisherImpl webSocketPublisher;

    @BeforeEach
    void setUp() {
        webSocketPublisher = new WebSocketPublisherImpl(messagingTemplate, userRepository, new AfterCommitExecutor());
    }

    @Test
    @DisplayName("Phát AuctionEvent BID_PLACED tới /topic/auctions/{id}")
    void publishAuctionEvent_BidPlaced_SendsToPluralTopic() {
        Instant now = Instant.parse("2026-11-20T02:00:00Z");
        BidHistoryItem bid = new BidHistoryItem(1L, BigDecimal.valueOf(500000), "N***h", now);
        AuctionEvent event = new AuctionEvent(
                AuctionEventType.BID_PLACED,
                10L,
                AuctionStatus.ACTIVE,
                BigDecimal.valueOf(500000),
                BigDecimal.valueOf(510000),
                1,
                "N***h",
                ReserveStatus.NO_RESERVE,
                now.minusSeconds(3600),
                now.plusSeconds(3600),
                bid,
                now
        );

        webSocketPublisher.publishAuctionEvent(event);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/auctions/10"), captor.capture());

        assertInstanceOf(AuctionEvent.class, captor.getValue());
        AuctionEvent sent = (AuctionEvent) captor.getValue();
        assertEquals(AuctionEventType.BID_PLACED, sent.type());
        assertEquals(10L, sent.auctionId());
        assertEquals(bid, sent.bid());
    }

    @Test
    @DisplayName("Phát AuctionEvent AUCTION_UPDATED không kèm bid tới /topic/auctions/{id}")
    void publishAuctionEvent_AuctionUpdated_SendsWithoutBid() {
        Instant now = Instant.parse("2026-11-20T02:00:00Z");
        AuctionEvent event = new AuctionEvent(
                AuctionEventType.AUCTION_UPDATED,
                10L,
                AuctionStatus.SCHEDULED,
                BigDecimal.valueOf(500000),
                BigDecimal.valueOf(500000),
                0,
                null,
                ReserveStatus.NOT_MET,
                now.plusSeconds(3600),
                now.plusSeconds(7200),
                null,
                now
        );

        webSocketPublisher.publishAuctionEvent(event);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/auctions/10"), captor.capture());

        AuctionEvent sent = (AuctionEvent) captor.getValue();
        assertEquals(AuctionEventType.AUCTION_UPDATED, sent.type());
        assertNull(sent.bid());
    }

    @Test
    @DisplayName("publishToUser(userId) tìm user và gửi tin nhắn tới /queue/notifications của email")
    void publishToUser_ByUserId_SendsToUserQueue() {
        User user = User.builder()
                .id(2L)
                .email("test@example.com")
                .build();

        when(userRepository.findById(2L)).thenReturn(Optional.of(user));

        String payload = "test payload";
        webSocketPublisher.publishToUser(2L, payload);

        verify(messagingTemplate).convertAndSendToUser("test@example.com", "/queue/notifications", payload);
    }
}
