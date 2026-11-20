package com.omnibid.scheduler;

import com.omnibid.service.OrderService;
import com.omnibid.service.PaymentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderSchedulerTest {

    @Mock
    private OrderService orderService;

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private OrderScheduler orderScheduler;

    @Test
    @DisplayName("OrderScheduler.tick() gọi timeoutStalePayments(), sendPaymentReminders(), expireOverdueOrders() và autoCompleteOrders()")
    void tick_ExecutesRemindersAndExpiry() {
        orderScheduler.tick();

        verify(paymentService).timeoutStalePayments();
        verify(orderService).sendPaymentReminders();
        verify(orderService).expireOverdueOrders();
        verify(orderService).autoCompleteOrders();
    }
}
