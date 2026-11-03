package com.omnibid.dto.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuctionWebSocketMessage {
    private String type;
    private Long auctionId;
    private Object data;
}
