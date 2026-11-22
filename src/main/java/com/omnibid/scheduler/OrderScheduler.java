package com.omnibid.scheduler;

import com.omnibid.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class OrderScheduler {

    private final OrderService orderService;
    private final Clock clock;

    public OrderScheduler(OrderService orderService) {
        this(orderService, Clock.systemUTC());
    }

    @Autowired
    public OrderScheduler(OrderService orderService, Clock clock) {
        this.orderService = orderService;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    @Scheduled(fixedDelayString = "${app.scheduler.interval-seconds:10}", timeUnit = TimeUnit.SECONDS)
    public void tick() {
        // 1. Hết hạn đơn hàng quá hạn thanh toán
        try {
            orderService.expireOverdueOrders();
        } catch (Exception ex) {
            log.error("Error in OrderScheduler.expireOverdueOrders", ex);
        }

        // 2. Tự động hoàn tất đơn hàng quá hạn xác nhận nhận hàng
        try {
            orderService.autoCompleteOrders();
        } catch (Exception ex) {
            log.error("Error in OrderScheduler.autoCompleteOrders", ex);
        }
    }

    public void tick(Instant now) {
        Instant effectiveNow = now != null ? now : Instant.now(clock);
        try {
            orderService.expireOverdueOrders(effectiveNow);
        } catch (Exception ex) {
            log.error("Error in OrderScheduler.expireOverdueOrders", ex);
        }

        try {
            orderService.autoCompleteOrders(effectiveNow);
        } catch (Exception ex) {
            log.error("Error in OrderScheduler.autoCompleteOrders", ex);
        }
    }
}
