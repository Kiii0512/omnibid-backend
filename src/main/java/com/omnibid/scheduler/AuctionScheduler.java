package com.omnibid.scheduler;

import com.omnibid.enums.AuctionStatus;
import com.omnibid.repository.AuctionRepository;
import com.omnibid.service.AuctionCloser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionScheduler {

    private final AuctionRepository auctionRepository;
    private final AuctionCloser auctionCloser;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${app.scheduler.interval-seconds:10}", timeUnit = TimeUnit.SECONDS)
    public void scheduleAuctionStatusTransitions() {
        Instant now = Instant.now(clock);

        try {
            List<Long> toStartIds = auctionRepository.findIdsByStatusAndStartTimeLessThanEqual(AuctionStatus.SCHEDULED, now);
            for (Long id : toStartIds) {
                try {
                    auctionCloser.startOne(id);
                } catch (Exception ex) {
                    log.error("Lỗi khi kích hoạt phiên đấu giá id={}", id, ex);
                }
            }
        } catch (Exception ex) {
            log.error("Lỗi truy vấn phiên cần kích hoạt", ex);
        }

        try {
            List<Long> toCloseIds = auctionRepository.findIdsByStatusAndEndTimeLessThanEqual(AuctionStatus.ACTIVE, now);
            for (Long id : toCloseIds) {
                try {
                    auctionCloser.closeOne(id);
                } catch (Exception ex) {
                    log.error("Lỗi khi chốt phiên đấu giá id={}", id, ex);
                }
            }
        } catch (Exception ex) {
            log.error("Lỗi truy vấn phiên cần chốt", ex);
        }
    }
}
