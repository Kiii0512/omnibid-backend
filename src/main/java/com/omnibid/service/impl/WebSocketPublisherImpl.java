package com.omnibid.service.impl;

import com.omnibid.common.AfterCommitExecutor;
import com.omnibid.dto.websocket.AuctionEvent;
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
}
