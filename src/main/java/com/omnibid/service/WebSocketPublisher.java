package com.omnibid.service;

public interface WebSocketPublisher {
    void publishAuctionUpdate(Long auctionId, Object payload);
}

