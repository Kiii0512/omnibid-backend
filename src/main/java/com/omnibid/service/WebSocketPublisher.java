package com.omnibid.service;

import com.omnibid.dto.websocket.AuctionEvent;

public interface WebSocketPublisher {
    void publishAuctionEvent(AuctionEvent event);
    void publishToUser(String username, Object payload);
    void publishToUser(Long userId, Object payload);
}
