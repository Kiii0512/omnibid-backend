package com.omnibid.scheduler;

import com.omnibid.service.AuctionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionScheduler {

    private final AuctionService auctionService;

    @Scheduled(fixedRate = 10000)
    public void scheduleAuctionStatusTransitions() {
        try {
            auctionService.startScheduledAuctions();
        } catch (Exception e) {
            log.error("Error during starting scheduled auctions", e);
        }

        try {
            auctionService.closeEndedAuctions();
        } catch (Exception e) {
            log.error("Error during closing ended auctions", e);
        }
    }
}

