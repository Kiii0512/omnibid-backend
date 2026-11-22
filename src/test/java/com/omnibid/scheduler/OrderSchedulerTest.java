package com.omnibid.scheduler;

import com.omnibid.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderSchedulerTest {

    @Mock
    private OrderService orderService;

    @InjectMocks
    private OrderScheduler orderScheduler;

    @Test
    @DisplayName("OrderScheduler.tick() gọi expireOverdueOrders() và autoCompleteOrders()")
    void tick_ExecutesExpiryAndAutoComplete() {
        orderScheduler.tick();

        verify(orderService).expireOverdueOrders();
        verify(orderService).autoCompleteOrders();
    }

    @Test
    @DisplayName("Lỗi ở expireOverdueOrders() không chặn autoCompleteOrders()")
    void tick_ContinuesWhenExpireThrows() {
        doThrow(new RuntimeException("DB error")).when(orderService).expireOverdueOrders();

        orderScheduler.tick();

        verify(orderService).expireOverdueOrders();
        verify(orderService).autoCompleteOrders();
    }
}
