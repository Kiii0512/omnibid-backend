package com.omnibid.service.impl;

import com.omnibid.dto.bid.BidResponse;
import com.omnibid.dto.websocket.AuctionWebSocketMessage;
import com.omnibid.repository.UserRepository;
import com.omnibid.service.WebSocketPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketPublisherImpl implements WebSocketPublisher {

    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;

    @Override
    public void publishAuctionUpdate(Long auctionId, Object payload) {
        AuctionWebSocketMessage wrapped = wrapAuctionMessage(auctionId, payload);
        String destination = "/topic/auction/" + auctionId;

        Runnable sendTask = () -> {
            try {
                log.info("Broadcasting auction update to {}: {}", destination, wrapped);
                messagingTemplate.convertAndSend(destination, wrapped);
            } catch (Exception ex) {
                log.warn("Failed to broadcast WebSocket auction update to {}: {}", destination, ex.getMessage(), ex);
            }
        };

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendTask.run();
                }
            });
        } else {
            sendTask.run();
        }
    }

    private AuctionWebSocketMessage wrapAuctionMessage(Long auctionId, Object payload) {
        if (payload instanceof AuctionWebSocketMessage msg) {
            return msg;
        }
        if (payload instanceof BidResponse) {
            return AuctionWebSocketMessage.builder()
                    .type("BID_PLACED")
                    .auctionId(auctionId)
                    .data(payload)
                    .build();
        }
        return AuctionWebSocketMessage.builder()
                .type("AUCTION_UPDATED")
                .auctionId(auctionId)
                .data(payload)
                .build();
    }

    @Override
    public void publishToUser(String username, Object payload) {
        try {
            log.info("Publishing WebSocket notification to user {}: {}", username, payload);
            messagingTemplate.convertAndSendToUser(username, "/queue/notifications", payload);
        } catch (Exception ex) {
            log.warn("Failed to send WebSocket notification to user {}: {}", username, ex.getMessage(), ex);
        }
    }

    @Override
    public void publishToUser(Long userId, Object payload) {
        userRepository.findById(userId).ifPresentOrElse(
                user -> publishToUser(user.getEmail(), payload),
                () -> log.warn("Cannot find user id={} to send WebSocket notification", userId)
        );
    }
}
