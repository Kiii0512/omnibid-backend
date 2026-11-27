package com.omnibid.service;

import com.omnibid.dto.websocket.AuctionEvent;

public interface WebSocketPublisher {
    void publishAuctionEvent(AuctionEvent event);
}
