package com.omnibid.service.impl;

import com.omnibid.common.AfterCommitExecutor;
import com.omnibid.dto.websocket.AuctionEvent;
import com.omnibid.repository.UserRepository;
import com.omnibid.service.WebSocketPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketPublisherImpl implements WebSocketPublisher {

    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;
    private final AfterCommitExecutor afterCommitExecutor;

    @Override
    public void publishAuctionEvent(AuctionEvent event) {
        if (event == null || event.auctionId() == null) {
            return;
        }
        String destination = "/topic/auctions/" + event.auctionId();
        afterCommitExecutor.runAfterCommit(() -> {
            log.info("Broadcasting auction event {} to {}", event.type(), destination);
            messagingTemplate.convertAndSend(destination, event);
        });
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
