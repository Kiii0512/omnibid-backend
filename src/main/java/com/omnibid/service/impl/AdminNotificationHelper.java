package com.omnibid.service.impl;

import com.omnibid.common.AfterCommitExecutor;
import com.omnibid.common.NotificationTemplates;
import com.omnibid.dto.websocket.AuctionEvent;
import com.omnibid.enums.AuctionEventType;
import com.omnibid.enums.AuctionStatus;
import com.omnibid.enums.NotificationType;
import com.omnibid.service.NotificationService;
import com.omnibid.service.WebSocketPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AdminNotificationHelper {

    private final NotificationService notificationService;
    private final WebSocketPublisher webSocketPublisher;
    private final AfterCommitExecutor afterCommitExecutor;

    public void notifyAuctionCancelled(Long aId, Long seller, Long leader, String reason, Instant now) {
        Runnable task = () -> {
            notificationService.notify(seller, NotificationType.AUCTION_CANCELLED,
                    NotificationTemplates.auctionCancelledTitle(), NotificationTemplates.auctionCancelledContent(aId, reason), "AUCTION", aId);
            if (leader != null && !leader.equals(seller)) {
                notificationService.notify(leader, NotificationType.AUCTION_CANCELLED,
                        NotificationTemplates.auctionCancelledTitle(), NotificationTemplates.auctionCancelledContent(aId, reason), "AUCTION", aId);
            }
            webSocketPublisher.publishAuctionEvent(AuctionEvent.builder()
                    .type(AuctionEventType.AUCTION_CANCELLED).auctionId(aId).status(AuctionStatus.CANCELLED).createdAt(now).build());
        };
        if (afterCommitExecutor != null) afterCommitExecutor.execute(task);
        else task.run();
    }

    public void notifyOrderCancelled(Long oId, Long userId, String reason) {
        Runnable task = () -> notificationService.notify(userId, NotificationType.ORDER_UPDATE,
                NotificationTemplates.orderCancelledTitle(oId), NotificationTemplates.orderCancelledContent(oId, reason), "ORDER", oId);
        if (afterCommitExecutor != null) afterCommitExecutor.execute(task);
        else task.run();
    }

    public void dispatchBanNotifications(List<AuctionCancelledRecord> aRecs, List<OrderCancelledRecord> oRecs, Instant now) {
        for (AuctionCancelledRecord ar : aRecs) notifyAuctionCancelled(ar.auctionId(), ar.sellerId(), ar.leaderId(), "Tài khoản người bán bị khóa", now);
        for (OrderCancelledRecord or : oRecs) notifyOrderCancelled(or.orderId(), or.otherPartyId(), "Tài khoản liên quan bị khóa");
    }

    public void dispatchHideProductNotifications(List<AuctionCancelledRecord> aRecs, List<OrderBothPartiesRecord> oRecs, Instant now) {
        for (AuctionCancelledRecord ar : aRecs) notifyAuctionCancelled(ar.auctionId(), ar.sellerId(), ar.leaderId(), "Sản phẩm bị ẩn", now);
        for (OrderBothPartiesRecord or : oRecs) {
            notifyOrderCancelled(or.orderId(), or.buyerId(), "Sản phẩm bị ẩn");
            notifyOrderCancelled(or.orderId(), or.sellerId(), "Sản phẩm bị ẩn");
        }
    }

    public record AuctionCancelledRecord(Long auctionId, Long sellerId, Long leaderId) {}
    public record OrderCancelledRecord(Long orderId, Long otherPartyId) {}
    public record OrderBothPartiesRecord(Long orderId, Long buyerId, Long sellerId) {}
}
