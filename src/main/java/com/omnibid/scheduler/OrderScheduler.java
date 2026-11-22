package com.omnibid.scheduler;

import com.omnibid.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderScheduler {

    private final OrderService orderService;

    @Scheduled(
            fixedDelayString = "${app.scheduler.interval-seconds:10}",
            initialDelayString = "${app.scheduler.interval-seconds:10}",
            timeUnit = TimeUnit.SECONDS
    )
    public void tick() {
        try {
            orderService.expireOverdueOrders();
        } catch (Exception ex) {
            log.error("Error in OrderScheduler.expireOverdueOrders", ex);
        }

        try {
            orderService.autoCompleteOrders();
        } catch (Exception ex) {
            log.error("Error in OrderScheduler.autoCompleteOrders", ex);
        }
    }
}
