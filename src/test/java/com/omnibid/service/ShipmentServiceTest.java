package com.omnibid.service;

import com.omnibid.common.AppException;
import com.omnibid.config.AppProperties;
import com.omnibid.dto.shipment.ShipmentDetailResponse;
import com.omnibid.dto.shipment.ShipmentStatusResponse;
import com.omnibid.dto.shipment.ShipmentStatusUpdateRequest;
import com.omnibid.entity.Order;
import com.omnibid.entity.Shipment;
import com.omnibid.entity.ShipmentEvent;
import com.omnibid.entity.User;
import com.omnibid.enums.*;
import com.omnibid.repository.OrderRepository;
import com.omnibid.repository.ShipmentEventRepository;
import com.omnibid.repository.ShipmentRepository;
import com.omnibid.repository.UserRepository;
import com.omnibid.security.CurrentUser;
import com.omnibid.service.impl.ShipmentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShipmentServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 10, 0);
    private final Clock clock = Clock.fixed(NOW.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant(), ZoneId.of("Asia/Ho_Chi_Minh"));

    private static final Long SELLER_ID = 1L;
    private static final Long BUYER_ID = 2L;
    private static final Long OTHER_ID = 3L;
    private static final Long ADMIN_ID = 9L;
    private static final Long ORDER_ID = 9L;
    private static final Long SHIPMENT_ID = 4L;

    private final CurrentUser buyerUser = new CurrentUser(BUYER_ID, false);
    private final CurrentUser sellerUser = new CurrentUser(SELLER_ID, false);
    private final CurrentUser otherUser = new CurrentUser(OTHER_ID, false);
    private final CurrentUser adminUser = new CurrentUser(ADMIN_ID, true);

    @Mock
    private ShipmentRepository shipmentRepository;

    @Mock
    private ShipmentEventRepository shipmentEventRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private NotificationService notificationService;

    private AppProperties appProperties;
    private ShipmentServiceImpl shipmentService;

    private Order sampleOrder;
    private Shipment sampleShipment;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        shipmentService = new ShipmentServiceImpl(
                shipmentRepository,
                shipmentEventRepository,
                orderRepository,
                userRepository,
                auditService,
                notificationService,
                appProperties,
                clock
        );

        sampleOrder = Order.builder()
                .id(ORDER_ID)
                .sellerId(SELLER_ID)
                .buyerId(BUYER_ID)
                .status(OrderStatus.PAID)
                .build();

        sampleShipment = Shipment.builder()
                .id(SHIPMENT_ID)
                .orderId(ORDER_ID)
                .trackingCode("SIM-000009")
                .carrierName("SIMULATED_CARRIER")
                .status(ShipmentStatus.CREATED)
                .build();
    }

    @Test
    @DisplayName("UT-SHP-01 Tạo vận đơn cho Order PAID thành công với tracking_code SIM-%06d và 1 event")
    void createForPaidOrder_Success() {
        when(shipmentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());
        when(shipmentRepository.save(any(Shipment.class))).thenAnswer(inv -> {
            Shipment s = inv.getArgument(0);
            s.setId(SHIPMENT_ID);
            return s;
        });

        Shipment created = shipmentService.createForPaidOrder(sampleOrder);

        assertThat(created).isNotNull();
        assertThat(created.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(created.getTrackingCode()).isEqualTo("SIM-000009");
        assertThat(created.getCarrierName()).isEqualTo("SIMULATED_CARRIER");
        assertThat(created.getStatus()).isEqualTo(ShipmentStatus.CREATED);

        ArgumentCaptor<ShipmentEvent> eventCaptor = ArgumentCaptor.forClass(ShipmentEvent.class);
        verify(shipmentEventRepository).save(eventCaptor.capture());
        ShipmentEvent event = eventCaptor.getValue();
        assertThat(event.getShipmentId()).isEqualTo(SHIPMENT_ID);
        assertThat(event.getStatus()).isEqualTo(ShipmentStatus.CREATED);
        assertThat(event.getNote()).isEqualTo("Vận đơn mô phỏng được tạo");
        assertThat(event.getUpdatedBy()).isNull();
    }

    @Test
    @DisplayName("UT-SHP-02 Tạo cho đơn chưa PAID ném 409 INVALID_STATUS_TRANSITION; gọi lần hai trả Shipment cũ")
    void createForPaidOrder_InvalidStatusAndIdempotent() {
        Order pendingOrder = Order.builder().id(ORDER_ID).status(OrderStatus.PENDING_PAYMENT).build();

        assertThatThrownBy(() -> shipmentService.createForPaidOrder(pendingOrder))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getCode()).isEqualTo("INVALID_STATUS_TRANSITION");
                    assertThat(appEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                });
        verify(shipmentRepository, never()).save(any());

        when(shipmentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(sampleShipment));
        Shipment result = shipmentService.createForPaidOrder(sampleOrder);
        assertThat(result).isSameAs(sampleShipment);
        verify(shipmentRepository, never()).save(any());
        verify(shipmentEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT-SHP-03 Xem vận đơn: BUYER, SELLER, ADMIN xem được, event by thuộc {SYSTEM, SELLER, ADMIN} không lộ id; OTHER 403; chưa PAID 404")
    void getShipmentByOrder_AuthorizationAndVisibility() {
        // Case 1: Order chưa PAID (PENDING_PAYMENT) -> 404
        sampleOrder.setStatus(OrderStatus.PENDING_PAYMENT);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(sampleOrder));

        assertThatThrownBy(() -> shipmentService.getShipmentByOrder(buyerUser, ORDER_ID))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getCode()).isEqualTo("NOT_FOUND"));

        // Case 2: OTHER xem -> 403
        sampleOrder.setStatus(OrderStatus.PAID);
        assertThatThrownBy(() -> shipmentService.getShipmentByOrder(otherUser, ORDER_ID))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getCode()).isEqualTo("FORBIDDEN"));

        // Case 3: BUYER xem được, event by = SYSTEM, SELLER, ADMIN không lộ id
        when(shipmentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(sampleShipment));
        ShipmentEvent e1 = ShipmentEvent.builder().shipmentId(SHIPMENT_ID).status(ShipmentStatus.CREATED).note("Tạo").updatedBy(null).createdAt(NOW).build();
        ShipmentEvent e2 = ShipmentEvent.builder().shipmentId(SHIPMENT_ID).status(ShipmentStatus.PICKED_UP).note("Giao").updatedBy(SELLER_ID).createdAt(NOW.plusHours(1)).build();
        ShipmentEvent e3 = ShipmentEvent.builder().shipmentId(SHIPMENT_ID).status(ShipmentStatus.IN_TRANSIT).note("Admin").updatedBy(ADMIN_ID).createdAt(NOW.plusHours(2)).build();
        when(shipmentEventRepository.findByShipmentIdOrderByCreatedAtAsc(SHIPMENT_ID)).thenReturn(List.of(e1, e2, e3));

        ShipmentDetailResponse resBuyer = shipmentService.getShipmentByOrder(buyerUser, ORDER_ID);
        assertThat(resBuyer.getEvents()).hasSize(3);
        assertThat(resBuyer.getEvents().get(0).getBy()).isEqualTo("SYSTEM");
        assertThat(resBuyer.getEvents().get(1).getBy()).isEqualTo("SELLER");
        assertThat(resBuyer.getEvents().get(2).getBy()).isEqualTo("ADMIN");

        // SELLER and ADMIN can also view
        assertThat(shipmentService.getShipmentByOrder(sellerUser, ORDER_ID)).isNotNull();
        assertThat(shipmentService.getShipmentByOrder(adminUser, ORDER_ID)).isNotNull();
    }

    @Test
    @DisplayName("UT-SHP-04 CREATED -> PICKED_UP bởi SELLER: Shipment PICKED_UP, Order sang SHIPPING, event updated_by = 1")
    void updateShipmentStatus_CreatedToPickedUp_Success() {
        when(shipmentRepository.findOrderIdById(SHIPMENT_ID)).thenReturn(Optional.of(ORDER_ID));
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(sampleOrder));
        when(shipmentRepository.findByIdForUpdate(SHIPMENT_ID)).thenReturn(Optional.of(sampleShipment));

        ShipmentStatusUpdateRequest req = new ShipmentStatusUpdateRequest(ShipmentStatus.PICKED_UP, "Đã bàn giao");
        ShipmentStatusResponse res = shipmentService.updateShipmentStatus(sellerUser, SHIPMENT_ID, req);

        assertThat(res.getStatus()).isEqualTo(ShipmentStatus.PICKED_UP);
        assertThat(res.getOrderStatus()).isEqualTo(OrderStatus.SHIPPING);
        assertThat(sampleOrder.getStatus()).isEqualTo(OrderStatus.SHIPPING);

        ArgumentCaptor<ShipmentEvent> captor = ArgumentCaptor.forClass(ShipmentEvent.class);
        verify(shipmentEventRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ShipmentStatus.PICKED_UP);
        assertThat(captor.getValue().getUpdatedBy()).isEqualTo(SELLER_ID);
    }

    @Test
    @DisplayName("UT-SHP-05 PICKED_UP -> IN_TRANSIT: Shipment IN_TRANSIT, Order giữ SHIPPING, ghi event")
    void updateShipmentStatus_PickedUpToInTransit_Success() {
        sampleShipment.setStatus(ShipmentStatus.PICKED_UP);
        sampleOrder.setStatus(OrderStatus.SHIPPING);

        when(shipmentRepository.findOrderIdById(SHIPMENT_ID)).thenReturn(Optional.of(ORDER_ID));
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(sampleOrder));
        when(shipmentRepository.findByIdForUpdate(SHIPMENT_ID)).thenReturn(Optional.of(sampleShipment));

        ShipmentStatusUpdateRequest req = new ShipmentStatusUpdateRequest(ShipmentStatus.IN_TRANSIT, "Đang vận chuyển");
        ShipmentStatusResponse res = shipmentService.updateShipmentStatus(sellerUser, SHIPMENT_ID, req);

        assertThat(res.getStatus()).isEqualTo(ShipmentStatus.IN_TRANSIT);
        assertThat(res.getOrderStatus()).isEqualTo(OrderStatus.SHIPPING);
        assertThat(sampleOrder.getStatus()).isEqualTo(OrderStatus.SHIPPING);
        verify(shipmentEventRepository).save(any(ShipmentEvent.class));
    }

    @Test
    @DisplayName("UT-SHP-06 IN_TRANSIT -> DELIVERED: Shipment DELIVERED, Order delivered_at = NOW, confirm_deadline = NOW + 7 ngày, giữ SHIPPING")
    void updateShipmentStatus_InTransitToDelivered_Success() {
        sampleShipment.setStatus(ShipmentStatus.IN_TRANSIT);
        sampleOrder.setStatus(OrderStatus.SHIPPING);

        when(shipmentRepository.findOrderIdById(SHIPMENT_ID)).thenReturn(Optional.of(ORDER_ID));
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(sampleOrder));
        when(shipmentRepository.findByIdForUpdate(SHIPMENT_ID)).thenReturn(Optional.of(sampleShipment));

        ShipmentStatusUpdateRequest req = new ShipmentStatusUpdateRequest(ShipmentStatus.DELIVERED, "Đã giao hàng");
        ShipmentStatusResponse res = shipmentService.updateShipmentStatus(sellerUser, SHIPMENT_ID, req);

        assertThat(res.getStatus()).isEqualTo(ShipmentStatus.DELIVERED);
        assertThat(sampleOrder.getStatus()).isEqualTo(OrderStatus.SHIPPING);
        assertThat(sampleOrder.getDeliveredAt()).isEqualTo(NOW);
        assertThat(sampleOrder.getConfirmDeadline()).isEqualTo(NOW.plusDays(7));
        assertThat(res.getDeliveredAt()).isEqualTo(NOW);
        assertThat(res.getConfirmDeadline()).isEqualTo(NOW.plusDays(7));
    }

    @Test
    @DisplayName("UT-SHP-07 Nhảy cóc: CREATED -> IN_TRANSIT hoặc CREATED -> DELIVERED ném 409 INVALID_STATUS_TRANSITION, không ghi event")
    void updateShipmentStatus_SkipStatus_ThrowsInvalidStatusTransition() {
        when(shipmentRepository.findOrderIdById(SHIPMENT_ID)).thenReturn(Optional.of(ORDER_ID));
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(sampleOrder));
        when(shipmentRepository.findByIdForUpdate(SHIPMENT_ID)).thenReturn(Optional.of(sampleShipment));

        // CREATED -> IN_TRANSIT
        ShipmentStatusUpdateRequest req1 = new ShipmentStatusUpdateRequest(ShipmentStatus.IN_TRANSIT, null);
        assertThatThrownBy(() -> shipmentService.updateShipmentStatus(sellerUser, SHIPMENT_ID, req1))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getCode()).isEqualTo("INVALID_STATUS_TRANSITION"));

        // CREATED -> DELIVERED
        ShipmentStatusUpdateRequest req2 = new ShipmentStatusUpdateRequest(ShipmentStatus.DELIVERED, null);
        assertThatThrownBy(() -> shipmentService.updateShipmentStatus(sellerUser, SHIPMENT_ID, req2))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getCode()).isEqualTo("INVALID_STATUS_TRANSITION"));

        verify(shipmentEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT-SHP-08 Đi lùi: IN_TRANSIT -> PICKED_UP hoặc DELIVERED -> IN_TRANSIT ném 409, không ghi event")
    void updateShipmentStatus_Backwards_ThrowsInvalidStatusTransition() {
        // IN_TRANSIT -> PICKED_UP
        sampleShipment.setStatus(ShipmentStatus.IN_TRANSIT);
        sampleOrder.setStatus(OrderStatus.SHIPPING);
        when(shipmentRepository.findOrderIdById(SHIPMENT_ID)).thenReturn(Optional.of(ORDER_ID));
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(sampleOrder));
        when(shipmentRepository.findByIdForUpdate(SHIPMENT_ID)).thenReturn(Optional.of(sampleShipment));

        ShipmentStatusUpdateRequest reqBack = new ShipmentStatusUpdateRequest(ShipmentStatus.PICKED_UP, null);
        assertThatThrownBy(() -> shipmentService.updateShipmentStatus(sellerUser, SHIPMENT_ID, reqBack))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getCode()).isEqualTo("INVALID_STATUS_TRANSITION"));

        // DELIVERED -> IN_TRANSIT
        sampleShipment.setStatus(ShipmentStatus.DELIVERED);
        ShipmentStatusUpdateRequest reqBack2 = new ShipmentStatusUpdateRequest(ShipmentStatus.IN_TRANSIT, null);
        assertThatThrownBy(() -> shipmentService.updateShipmentStatus(sellerUser, SHIPMENT_ID, reqBack2))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getCode()).isEqualTo("INVALID_STATUS_TRANSITION"));

        verify(shipmentEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT-SHP-09 Lặp trạng thái: PICKED_UP -> PICKED_UP ném 409, không ghi event")
    void updateShipmentStatus_SameStatus_ThrowsInvalidStatusTransition() {
        sampleShipment.setStatus(ShipmentStatus.PICKED_UP);
        sampleOrder.setStatus(OrderStatus.SHIPPING);
        when(shipmentRepository.findOrderIdById(SHIPMENT_ID)).thenReturn(Optional.of(ORDER_ID));
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(sampleOrder));
        when(shipmentRepository.findByIdForUpdate(SHIPMENT_ID)).thenReturn(Optional.of(sampleShipment));

        ShipmentStatusUpdateRequest reqSame = new ShipmentStatusUpdateRequest(ShipmentStatus.PICKED_UP, null);
        assertThatThrownBy(() -> shipmentService.updateShipmentStatus(sellerUser, SHIPMENT_ID, reqSame))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getCode()).isEqualTo("INVALID_STATUS_TRANSITION"));

        verify(shipmentEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT-SHP-10 Buyer cập nhật ném 403 FORBIDDEN")
    void updateShipmentStatus_BuyerUpdates_ThrowsForbidden() {
        when(shipmentRepository.findOrderIdById(SHIPMENT_ID)).thenReturn(Optional.of(ORDER_ID));
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(sampleOrder));
        when(shipmentRepository.findByIdForUpdate(SHIPMENT_ID)).thenReturn(Optional.of(sampleShipment));

        ShipmentStatusUpdateRequest req = new ShipmentStatusUpdateRequest(ShipmentStatus.PICKED_UP, null);
        assertThatThrownBy(() -> shipmentService.updateShipmentStatus(buyerUser, SHIPMENT_ID, req))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getCode()).isEqualTo("FORBIDDEN"));
    }

    @Test
    @DisplayName("UT-SHP-11 Người ngoài nhận 403; ADMIN cập nhật thành công, event updated_by = 9, ghi audit SHIPMENT_UPDATE")
    void updateShipmentStatus_OtherForbiddenAndAdminSuccess() {
        when(shipmentRepository.findOrderIdById(SHIPMENT_ID)).thenReturn(Optional.of(ORDER_ID));
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(sampleOrder));
        when(shipmentRepository.findByIdForUpdate(SHIPMENT_ID)).thenReturn(Optional.of(sampleShipment));

        // OTHER -> 403
        ShipmentStatusUpdateRequest req = new ShipmentStatusUpdateRequest(ShipmentStatus.PICKED_UP, "Bàn giao");
        assertThatThrownBy(() -> shipmentService.updateShipmentStatus(otherUser, SHIPMENT_ID, req))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getCode()).isEqualTo("FORBIDDEN"));

        // ADMIN -> Thành công, event updated_by = 9, ghi audit
        ShipmentStatusResponse res = shipmentService.updateShipmentStatus(adminUser, SHIPMENT_ID, req);
        assertThat(res.getStatus()).isEqualTo(ShipmentStatus.PICKED_UP);

        ArgumentCaptor<ShipmentEvent> eventCaptor = ArgumentCaptor.forClass(ShipmentEvent.class);
        verify(shipmentEventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getUpdatedBy()).isEqualTo(ADMIN_ID);

        verify(auditService).log(eq(ADMIN_ID), eq("SHIPMENT_UPDATE"), eq("SHIPMENT"), eq(SHIPMENT_ID), anyString());
    }

    @Test
    @DisplayName("UT-SHP-12 Seller bị khóa (BANNED) ném 403 ACCOUNT_BANNED")
    void updateShipmentStatus_SellerBanned_ThrowsAccountBanned() {
        when(shipmentRepository.findOrderIdById(SHIPMENT_ID)).thenReturn(Optional.of(ORDER_ID));
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(sampleOrder));
        when(shipmentRepository.findByIdForUpdate(SHIPMENT_ID)).thenReturn(Optional.of(sampleShipment));

        User bannedSeller = User.builder().id(SELLER_ID).status(UserStatus.BANNED).build();
        when(userRepository.findById(SELLER_ID)).thenReturn(Optional.of(bannedSeller));

        ShipmentStatusUpdateRequest req = new ShipmentStatusUpdateRequest(ShipmentStatus.PICKED_UP, null);
        assertThatThrownBy(() -> shipmentService.updateShipmentStatus(sellerUser, SHIPMENT_ID, req))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getCode()).isEqualTo("ACCOUNT_BANNED");
                    assertThat(appEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                });
    }

    @Test
    @DisplayName("UT-SHP-13 Đơn CANCELLED ném 409 ORDER_CANCELLED; Đơn COMPLETED ném 409 INVALID_STATUS_TRANSITION")
    void updateShipmentStatus_CancelledOrCompletedOrder_ThrowsConflict() {
        when(shipmentRepository.findOrderIdById(SHIPMENT_ID)).thenReturn(Optional.of(ORDER_ID));
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(sampleOrder));
        when(shipmentRepository.findByIdForUpdate(SHIPMENT_ID)).thenReturn(Optional.of(sampleShipment));

        sampleOrder.setStatus(OrderStatus.CANCELLED);
        ShipmentStatusUpdateRequest req = new ShipmentStatusUpdateRequest(ShipmentStatus.PICKED_UP, null);
        assertThatThrownBy(() -> shipmentService.updateShipmentStatus(sellerUser, SHIPMENT_ID, req))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getCode()).isEqualTo("ORDER_CANCELLED"));

        sampleOrder.setStatus(OrderStatus.COMPLETED);
        assertThatThrownBy(() -> shipmentService.updateShipmentStatus(sellerUser, SHIPMENT_ID, req))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getCode()).isEqualTo("INVALID_STATUS_TRANSITION"));
    }

    @Test
    @DisplayName("UT-SHP-14 Lịch sử bất biến: Không có thao tác update/delete trên ShipmentEventRepository")
    void updateShipmentStatus_ImmutableHistory_NeverUpdatesOrDeletesEvents() {
        when(shipmentRepository.findOrderIdById(SHIPMENT_ID)).thenReturn(Optional.of(ORDER_ID));
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(sampleOrder));
        when(shipmentRepository.findByIdForUpdate(SHIPMENT_ID)).thenReturn(Optional.of(sampleShipment));

        ShipmentStatusUpdateRequest req = new ShipmentStatusUpdateRequest(ShipmentStatus.PICKED_UP, "Giao");
        shipmentService.updateShipmentStatus(sellerUser, SHIPMENT_ID, req);

        verify(shipmentEventRepository, times(1)).save(any(ShipmentEvent.class));
        verify(shipmentEventRepository, never()).delete(any());
        verify(shipmentEventRepository, never()).deleteAll();
    }

    @Test
    @DisplayName("UT-SHP-15 Thông báo SHIPMENT_UPDATE cho buyer mỗi lần đổi trạng thái; DELIVERED nêu hạn xác nhận")
    void updateShipmentStatus_NotificationSentToBuyer() {
        sampleShipment.setStatus(ShipmentStatus.IN_TRANSIT);
        sampleOrder.setStatus(OrderStatus.SHIPPING);

        when(shipmentRepository.findOrderIdById(SHIPMENT_ID)).thenReturn(Optional.of(ORDER_ID));
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(sampleOrder));
        when(shipmentRepository.findByIdForUpdate(SHIPMENT_ID)).thenReturn(Optional.of(sampleShipment));

        ShipmentStatusUpdateRequest req = new ShipmentStatusUpdateRequest(ShipmentStatus.DELIVERED, "Giao xong");
        shipmentService.updateShipmentStatus(sellerUser, SHIPMENT_ID, req);

        ArgumentCaptor<String> titleCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);

        verify(notificationService).notify(
                eq(BUYER_ID),
                eq(NotificationType.SHIPMENT_UPDATE),
                titleCaptor.capture(),
                contentCaptor.capture(),
                eq("SHIPMENT"),
                eq(SHIPMENT_ID)
        );

        assertThat(titleCaptor.getValue()).contains("DELIVERED");
        assertThat(contentCaptor.getValue()).contains("10:00 09/11/2026");
    }
}
