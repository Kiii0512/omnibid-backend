package com.omnibid.service.impl;

import com.omnibid.dto.websocket.AuctionEvent;
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
    public void publishAuctionEvent(AuctionEvent event) {
        if (event == null || event.auctionId() == null) {
            return;
        }
        String destination = "/topic/auctions/" + event.auctionId();
        Runnable sendTask = () -> {
            try {
                log.info("Broadcasting AuctionEvent to {}: type={}, status={}",
                        destination, event.type(), event.status());
                messagingTemplate.convertAndSend(destination, event);
            } catch (Exception ex) {
                log.warn("Failed to broadcast WebSocket AuctionEvent to {}: {}", destination, ex.getMessage());
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

    @Override
    public void publishAuctionUpdate(Long auctionId, Object payload) {
        String destination = "/topic/auctions/" + auctionId;
        Runnable sendTask = () -> {
            try {
                log.info("Broadcasting auction update to {}: {}", destination, payload);
                messagingTemplate.convertAndSend(destination, payload);
            } catch (Exception ex) {
                log.warn("Failed to broadcast WebSocket auction update to {}: {}", destination, ex.getMessage());
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

    @Override
    public void publishToUser(String username, Object payload) {
        try {
            log.info("Publishing WebSocket notification to user {}: {}", username, payload);
            messagingTemplate.convertAndSendToUser(username, "/queue/notifications", payload);
        } catch (Exception ex) {
            log.warn("Failed to send WebSocket notification to user {}: {}", username, ex.getMessage());
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
