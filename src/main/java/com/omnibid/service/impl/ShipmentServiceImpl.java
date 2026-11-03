package com.omnibid.service.impl;

import com.omnibid.common.AppException;
import com.omnibid.common.NotificationTemplates;
import com.omnibid.config.AppProperties;
import com.omnibid.dto.shipment.ShipmentDetailResponse;
import com.omnibid.dto.shipment.ShipmentEventDto;
import com.omnibid.dto.shipment.ShipmentStatusResponse;
import com.omnibid.dto.shipment.ShipmentStatusUpdateRequest;
import com.omnibid.entity.Order;
import com.omnibid.entity.Shipment;
import com.omnibid.entity.ShipmentEvent;
import com.omnibid.enums.NotificationType;
import com.omnibid.enums.OrderStatus;
import com.omnibid.enums.ShipmentStatus;
import com.omnibid.enums.UserStatus;
import com.omnibid.repository.OrderRepository;
import com.omnibid.repository.ShipmentEventRepository;
import com.omnibid.repository.ShipmentRepository;
import com.omnibid.repository.UserRepository;
import com.omnibid.security.CurrentUser;
import com.omnibid.service.AuditService;
import com.omnibid.service.NotificationService;
import com.omnibid.service.ShipmentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ShipmentServiceImpl implements ShipmentService {

    private final ShipmentRepository shipmentRepository;
    private final ShipmentEventRepository shipmentEventRepository;
    private OrderRepository orderRepository;
    private UserRepository userRepository;
    private AuditService auditService;
    private NotificationService notificationService;
    private AppProperties appProperties;
    private Clock clock;

    public ShipmentServiceImpl(ShipmentRepository shipmentRepository,
                               ShipmentEventRepository shipmentEventRepository) {
        this.shipmentRepository = shipmentRepository;
        this.shipmentEventRepository = shipmentEventRepository;
    }

    @Autowired
    public ShipmentServiceImpl(ShipmentRepository shipmentRepository,
                               ShipmentEventRepository shipmentEventRepository,
                               OrderRepository orderRepository,
                               UserRepository userRepository,
                               AuditService auditService,
                               NotificationService notificationService,
                               AppProperties appProperties,
                               Clock clock) {
        this.shipmentRepository = shipmentRepository;
        this.shipmentEventRepository = shipmentEventRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.appProperties = appProperties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Shipment createForPaidOrder(Order order) {
        if (order.getStatus() != OrderStatus.PAID) {
            throw new AppException("INVALID_STATUS_TRANSITION", "Đơn hàng chưa ở trạng thái PAID", HttpStatus.CONFLICT);
        }

        Optional<Shipment> existing = shipmentRepository.findByOrderId(order.getId());
        if (existing.isPresent()) {
            return existing.get();
        }

        String trackingCode = String.format("SIM-%06d", order.getId());
        Shipment shipment = Shipment.builder()
                .orderId(order.getId())
                .trackingCode(trackingCode)
                .carrierName("SIMULATED_CARRIER")
                .status(ShipmentStatus.CREATED)
                .build();
        shipment = shipmentRepository.save(shipment);

        ShipmentEvent event = ShipmentEvent.builder()
                .shipmentId(shipment.getId())
                .status(ShipmentStatus.CREATED)
                .note("Vận đơn mô phỏng được tạo")
                .updatedBy(null)
                .build();
        shipmentEventRepository.save(event);

        return shipment;
    }

    @Override
    public ShipmentDetailResponse getShipmentByOrder(CurrentUser currentUser, Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(
                () -> new AppException("NOT_FOUND", "Không tìm thấy đơn hàng", HttpStatus.NOT_FOUND)
        );

        if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            throw new AppException("NOT_FOUND", "Đơn hàng chưa thanh toán", HttpStatus.NOT_FOUND);
        }

        boolean isBuyer = order.getBuyerId().equals(currentUser.id());
        boolean isSeller = order.getSellerId().equals(currentUser.id());
        boolean isAdmin = currentUser.isAdmin();

        if (!isBuyer && !isSeller && !isAdmin) {
            throw new AppException("FORBIDDEN", "Bạn không có quyền xem vận đơn này", HttpStatus.FORBIDDEN);
        }

        Shipment shipment = shipmentRepository.findByOrderId(orderId).orElseThrow(
                () -> new AppException("NOT_FOUND", "Không tìm thấy vận đơn", HttpStatus.NOT_FOUND)
        );

        List<ShipmentEvent> events = shipmentEventRepository.findByShipmentIdOrderByCreatedAtAsc(shipment.getId());
        List<ShipmentEventDto> eventDtos = events.stream()
                .map(e -> {
                    String by;
                    if (e.getUpdatedBy() == null) {
                        by = "SYSTEM";
                    } else if (e.getUpdatedBy().equals(order.getSellerId())) {
                        by = "SELLER";
                    } else {
                        by = "ADMIN";
                    }
                    return ShipmentEventDto.builder()
                            .status(e.getStatus())
                            .note(e.getNote())
                            .by(by)
                            .createdAt(e.getCreatedAt())
                            .build();
                })
                .collect(Collectors.toList());

        return ShipmentDetailResponse.builder()
                .id(shipment.getId())
                .orderId(order.getId())
                .trackingCode(shipment.getTrackingCode())
                .carrierName(shipment.getCarrierName())
                .status(shipment.getStatus())
                .events(eventDtos)
                .build();
    }

    @Override
    @Transactional
    public ShipmentStatusResponse updateShipmentStatus(CurrentUser currentUser, Long shipmentId, ShipmentStatusUpdateRequest request) {
        Shipment pre = shipmentRepository.findById(shipmentId).orElseThrow(
                () -> new AppException("NOT_FOUND", "Không tìm thấy vận đơn", HttpStatus.NOT_FOUND)
        );

        // Lock order first, then lock shipment (SHP-07)
        Order order = orderRepository.findByIdForUpdate(pre.getOrderId()).orElseThrow(
                () -> new AppException("NOT_FOUND", "Không tìm thấy đơn hàng", HttpStatus.NOT_FOUND)
        );

        Shipment shipment = shipmentRepository.findByIdForUpdate(shipmentId).orElseThrow(
                () -> new AppException("NOT_FOUND", "Không tìm thấy vận đơn", HttpStatus.NOT_FOUND)
        );

        // Account banned check
        if (userRepository != null) {
            userRepository.findById(currentUser.id()).ifPresent(user -> {
                if (user.getStatus() == UserStatus.BANNED) {
                    throw new AppException("ACCOUNT_BANNED", "Tài khoản của bạn đã bị khóa", HttpStatus.FORBIDDEN);
                }
            });
        }

        // Permission check
        boolean isSeller = order.getSellerId().equals(currentUser.id());
        boolean isAdmin = currentUser.isAdmin();
        if (!isSeller && !isAdmin) {
            throw new AppException("FORBIDDEN", "Chỉ người bán của đơn hoặc Quản trị viên mới được cập nhật vận đơn", HttpStatus.FORBIDDEN);
        }

        // Order conditions
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new AppException("ORDER_CANCELLED", "Đơn hàng đã bị hủy", HttpStatus.CONFLICT);
        }
        if (order.getStatus() != OrderStatus.PAID && order.getStatus() != OrderStatus.SHIPPING) {
            throw new AppException("INVALID_STATUS_TRANSITION", "Trạng thái đơn hàng không hợp lệ để cập nhật vận đơn", HttpStatus.CONFLICT);
        }

        // Status transition sequence validation: only direct next transition
        ShipmentStatus current = shipment.getStatus();
        ShipmentStatus target = request.getStatus();

        boolean isValidTransition = (current == ShipmentStatus.CREATED && target == ShipmentStatus.PICKED_UP)
                || (current == ShipmentStatus.PICKED_UP && target == ShipmentStatus.IN_TRANSIT)
                || (current == ShipmentStatus.IN_TRANSIT && target == ShipmentStatus.DELIVERED);

        if (!isValidTransition) {
            throw new AppException("INVALID_STATUS_TRANSITION", "Chuyển trạng thái vận chuyển không hợp lệ", HttpStatus.CONFLICT);
        }

        // Update shipment status
        shipment.setStatus(target);
        shipmentRepository.save(shipment);

        // Create immutable event
        String note = request.getNote();
        if (note == null || note.isBlank()) {
            note = switch (target) {
                case PICKED_UP -> "Đã bàn giao (mô phỏng)";
                case IN_TRANSIT -> "Đang vận chuyển (mô phỏng)";
                case DELIVERED -> "Đã giao hàng (mô phỏng)";
                default -> "Cập nhật vận đơn";
            };
        }

        ShipmentEvent event = ShipmentEvent.builder()
                .shipmentId(shipment.getId())
                .status(target)
                .note(note)
                .updatedBy(currentUser.id())
                .build();
        shipmentEventRepository.save(event);

        // Effect on Order
        LocalDateTime now = clock != null ? LocalDateTime.now(clock) : LocalDateTime.now();
        if (target == ShipmentStatus.PICKED_UP) {
            if (order.getStatus() == OrderStatus.PAID) {
                order.markShipping();
                orderRepository.save(order);
            }
        } else if (target == ShipmentStatus.DELIVERED) {
            int windowDays = appProperties != null ? appProperties.getOrder().getConfirmWindowDays() : 7;
            order.markDelivered(now, now.plusDays(windowDays));
            orderRepository.save(order);
        }

        // Admin audit
        if (isAdmin && auditService != null) {
            auditService.log(currentUser.id(), "SHIPMENT_UPDATE", "SHIPMENT", shipment.getId(), "Cập nhật trạng thái " + target);
        }

        // Notification
        if (notificationService != null) {
            notificationService.notify(
                    order.getBuyerId(),
                    NotificationType.SHIPMENT_UPDATE,
                    NotificationTemplates.shipmentUpdateTitle(shipment.getTrackingCode(), target.name()),
                    NotificationTemplates.shipmentUpdateContent(shipment.getTrackingCode(), order.getId(), target.name(),
                            target == ShipmentStatus.DELIVERED ? order.getConfirmDeadline() : null),
                    "SHIPMENT",
                    shipment.getId()
            );
        }

        return ShipmentStatusResponse.builder()
                .id(shipment.getId())
                .status(shipment.getStatus())
                .orderStatus(order.getStatus())
                .deliveredAt(target == ShipmentStatus.DELIVERED ? order.getDeliveredAt() : null)
                .confirmDeadline(target == ShipmentStatus.DELIVERED ? order.getConfirmDeadline() : null)
                .build();
    }
}
