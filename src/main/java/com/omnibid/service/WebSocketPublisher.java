package com.omnibid.service;

public interface WebSocketPublisher {
    void publishAuctionUpdate(Long auctionId, Object payload);
    void publishToUser(String username, Object payload);
    void publishToUser(Long userId, Object payload);
}
