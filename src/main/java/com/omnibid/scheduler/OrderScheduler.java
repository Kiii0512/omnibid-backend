package com.omnibid.scheduler;

import com.omnibid.service.OrderService;
import com.omnibid.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderScheduler {

    private final OrderService orderService;
    private final PaymentService paymentService;

    @Scheduled(fixedDelayString = "${app.scheduler.order-delay:10000}", initialDelay = 10000)
    public void tick() {
        // 1. PaymentService.timeoutStalePayments() (PAY-12)
        try {
            paymentService.timeoutStalePayments();
        } catch (Exception ex) {
            log.error("Error in OrderScheduler.timeoutStalePayments", ex);
        }

        // 2. Nhắc thanh toán 24h và 2h (ORD-07)
        try {
            orderService.sendPaymentReminders();
        } catch (Exception ex) {
            log.error("Error in OrderScheduler.sendPaymentReminders", ex);
        }

        // 3. Hết hạn đơn hàng quá hạn thanh toán (ORD-06)
        try {
            orderService.expireOverdueOrders();
        } catch (Exception ex) {
            log.error("Error in OrderScheduler.expireOverdueOrders", ex);
        }

        // 4. OrderService.autoCompleteOrders() (ORD-09)
        try {
            orderService.autoCompleteOrders();
        } catch (Exception ex) {
            log.error("Error in OrderScheduler.autoCompleteOrders", ex);
        }
    }
}
