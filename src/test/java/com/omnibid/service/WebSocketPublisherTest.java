package com.omnibid.service;

import com.omnibid.dto.auction.AuctionResponse;
import com.omnibid.dto.bid.BidResponse;
import com.omnibid.dto.websocket.AuctionWebSocketMessage;
import com.omnibid.entity.User;
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
        webSocketPublisher = new WebSocketPublisherImpl(messagingTemplate, userRepository);
    }

    @Test
    @DisplayName("G-09 Bọc payload BidResponse thành {type: 'BID_PLACED', auctionId, data}")
    void publishAuctionUpdate_BidResponse_WrapsProperly() {
        BidResponse bidResponse = BidResponse.builder()
                .id(1L)
                .auctionId(10L)
                .bidderName("N***n")
                .amount(BigDecimal.valueOf(500000))
                .build();

        webSocketPublisher.publishAuctionUpdate(10L, bidResponse);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/auction/10"), captor.capture());

        Object captured = captor.getValue();
        assertInstanceOf(AuctionWebSocketMessage.class, captured);
        AuctionWebSocketMessage message = (AuctionWebSocketMessage) captured;
        assertEquals("BID_PLACED", message.getType());
        assertEquals(10L, message.getAuctionId());
        assertEquals(bidResponse, message.getData());
    }

    @Test
    @DisplayName("G-09 Bọc payload AuctionResponse thành {type: 'AUCTION_UPDATED', auctionId, data}")
    void publishAuctionUpdate_AuctionResponse_WrapsProperly() {
        AuctionResponse auctionResponse = AuctionResponse.builder()
                .id(10L)
                .currentPrice(BigDecimal.valueOf(500000))
                .build();

        webSocketPublisher.publishAuctionUpdate(10L, auctionResponse);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/auction/10"), captor.capture());

        Object captured = captor.getValue();
        assertInstanceOf(AuctionWebSocketMessage.class, captured);
        AuctionWebSocketMessage message = (AuctionWebSocketMessage) captured;
        assertEquals("AUCTION_UPDATED", message.getType());
        assertEquals(10L, message.getAuctionId());
        assertEquals(auctionResponse, message.getData());
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
