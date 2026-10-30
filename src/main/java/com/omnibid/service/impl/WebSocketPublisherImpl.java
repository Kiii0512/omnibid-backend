package com.omnibid.service.impl;

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

    @Override
    public void publishAuctionUpdate(Long auctionId, Object payload) {
        String destination = "/topic/auction/" + auctionId;
        log.info("Broadcasting auction update to {}: {}", destination, payload);
        messagingTemplate.convertAndSend(destination, payload);
    }
}

