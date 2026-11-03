package com.omnibid.service;

import com.omnibid.common.AppException;
import com.omnibid.entity.Order;
import com.omnibid.entity.Shipment;
import com.omnibid.entity.ShipmentEvent;
import com.omnibid.enums.OrderStatus;
import com.omnibid.enums.ShipmentStatus;
import com.omnibid.repository.ShipmentEventRepository;
import com.omnibid.repository.ShipmentRepository;
import com.omnibid.service.impl.ShipmentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShipmentServiceTest {

    @Mock
    private ShipmentRepository shipmentRepository;

    @Mock
    private ShipmentEventRepository shipmentEventRepository;

    private ShipmentServiceImpl shipmentService;

    @BeforeEach
    void setUp() {
        shipmentService = new ShipmentServiceImpl(shipmentRepository, shipmentEventRepository);
    }

    @Test
    @DisplayName("UT-SHP-01 Tạo vận đơn cho Order PAID thành công với tracking_code SIM-%06d và 1 event")
    void createForPaidOrder_Success() {
        Order order = Order.builder()
                .id(9L)
                .status(OrderStatus.PAID)
                .build();

        when(shipmentRepository.findByOrderId(9L)).thenReturn(Optional.empty());
        when(shipmentRepository.save(any(Shipment.class))).thenAnswer(inv -> {
            Shipment s = inv.getArgument(0);
            s.setId(101L);
            return s;
        });

        Shipment created = shipmentService.createForPaidOrder(order);

        assertThat(created).isNotNull();
        assertThat(created.getOrderId()).isEqualTo(9L);
        assertThat(created.getTrackingCode()).isEqualTo("SIM-000009");
        assertThat(created.getCarrierName()).isEqualTo("SIMULATED_CARRIER");
        assertThat(created.getStatus()).isEqualTo(ShipmentStatus.CREATED);

        ArgumentCaptor<ShipmentEvent> eventCaptor = ArgumentCaptor.forClass(ShipmentEvent.class);
        verify(shipmentEventRepository).save(eventCaptor.capture());
        ShipmentEvent event = eventCaptor.getValue();
        assertThat(event.getShipmentId()).isEqualTo(101L);
        assertThat(event.getStatus()).isEqualTo(ShipmentStatus.CREATED);
        assertThat(event.getNote()).isEqualTo("Vận đơn mô phỏng được tạo");
        assertThat(event.getUpdatedBy()).isNull();
    }

    @Test
    @DisplayName("UT-SHP-02 Tạo cho đơn chưa PAID ném 409 INVALID_STATUS_TRANSITION; gọi lần hai trả Shipment cũ")
    void createForPaidOrder_InvalidStatusAndIdempotent() {
        // Case 1: Order PENDING_PAYMENT -> 409
        Order pendingOrder = Order.builder()
                .id(9L)
                .status(OrderStatus.PENDING_PAYMENT)
                .build();

        assertThatThrownBy(() -> shipmentService.createForPaidOrder(pendingOrder))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getCode()).isEqualTo("INVALID_STATUS_TRANSITION");
                    assertThat(appEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                });
        verify(shipmentRepository, never()).save(any());

        // Case 2: Order PAID but shipment already exists -> returns old shipment without save
        Order paidOrder = Order.builder()
                .id(9L)
                .status(OrderStatus.PAID)
                .build();
        Shipment existing = Shipment.builder()
                .id(101L)
                .orderId(9L)
                .trackingCode("SIM-000009")
                .status(ShipmentStatus.CREATED)
                .build();
        when(shipmentRepository.findByOrderId(9L)).thenReturn(Optional.of(existing));

        Shipment result = shipmentService.createForPaidOrder(paidOrder);
        assertThat(result).isSameAs(existing);
        verify(shipmentRepository, never()).save(any());
        verify(shipmentEventRepository, never()).save(any());
    }
}
