package com.omnibid.service.impl;

import com.omnibid.common.AppException;
import com.omnibid.common.MaskingUtils;
import com.omnibid.common.PageResponse;
import com.omnibid.config.AppProperties;
import com.omnibid.dto.order.OrderDetailResponse;
import com.omnibid.dto.order.OrderResponse;
import com.omnibid.dto.order.ShippingInfoRequest;
import com.omnibid.entity.*;
import com.omnibid.enums.OrderStatus;
import com.omnibid.enums.ProductStatus;
import com.omnibid.enums.ReportStatus;
import com.omnibid.enums.ReportType;
import com.omnibid.enums.ShipmentStatus;
import com.omnibid.repository.*;
import com.omnibid.security.CurrentUser;
import com.omnibid.security.OrderAccessPolicy;
import com.omnibid.service.NotificationService;
import com.omnibid.service.OrderLifecycleSupport;
import com.omnibid.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final ShipmentRepository shipmentRepository;
    private final ReportRepository reportRepository;
    private final OrderAccessPolicy orderAccessPolicy;
    private final OrderLifecycleSupport orderLifecycleSupport;
    private final AppProperties appProperties;
    private final Clock clock;
    private NotificationService notificationService;

    @org.springframework.beans.factory.annotation.Autowired
    public OrderServiceImpl(OrderRepository orderRepository,
                            ProductRepository productRepository,
                            ProductImageRepository productImageRepository,
                            UserRepository userRepository,
                            PaymentRepository paymentRepository,
                            ShipmentRepository shipmentRepository,
                            ReportRepository reportRepository,
                            OrderAccessPolicy orderAccessPolicy,
                            OrderLifecycleSupport orderLifecycleSupport,
                            AppProperties appProperties,
                            Clock clock,
                            NotificationService notificationService) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.productImageRepository = productImageRepository;
        this.userRepository = userRepository;
        this.paymentRepository = paymentRepository;
        this.shipmentRepository = shipmentRepository;
        this.reportRepository = reportRepository;
        this.orderAccessPolicy = orderAccessPolicy;
        this.orderLifecycleSupport = orderLifecycleSupport;
        this.appProperties = appProperties;
        this.clock = clock;
        this.notificationService = notificationService;
    }

    public OrderServiceImpl(OrderRepository orderRepository,
                            ProductRepository productRepository,
                            ProductImageRepository productImageRepository,
                            UserRepository userRepository,
                            PaymentRepository paymentRepository,
                            ShipmentRepository shipmentRepository,
                            ReportRepository reportRepository,
                            OrderAccessPolicy orderAccessPolicy,
                            OrderLifecycleSupport orderLifecycleSupport,
                            AppProperties appProperties,
                            Clock clock) {
        this(orderRepository, productRepository, productImageRepository, userRepository, paymentRepository, shipmentRepository, reportRepository, orderAccessPolicy, orderLifecycleSupport, appProperties, clock, null);
    }

    // Overloaded constructor for tests only testing createFromAuction
    public OrderServiceImpl(OrderRepository orderRepository, AppProperties appProperties) {
        this(orderRepository, null, null, null, null, null, null, null, null, appProperties, Clock.systemDefaultZone());
    }

    @Override
    @Transactional
    public Order createFromAuction(Auction auction) {
        return orderRepository.findByAuctionId(auction.getId())
                .orElseGet(() -> {
                    int deadlineHours = appProperties.getOrder().getPaymentDeadlineHours();
                    LocalDateTime closedAt = auction.getClosedAt() != null ? auction.getClosedAt() : LocalDateTime.now(clock);
                    Order order = Order.builder()
                            .auctionId(auction.getId())
                            .productId(auction.getProductId())
                            .sellerId(auction.getSellerId())
                            .buyerId(auction.getWinnerId())
                            .finalPrice(auction.getFinalPrice())
                            .status(OrderStatus.PENDING_PAYMENT)
                            .paymentDeadline(closedAt.plusHours(deadlineHours))
                            .build();
                    log.info("Creating order for auction {}: buyer={}, amount={}",
                            auction.getId(), auction.getWinnerId(), auction.getFinalPrice());
                    return orderRepository.save(order);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getOrders(CurrentUser currentUser, String role, OrderStatus status, Pageable pageable) {
        Page<Order> page;
        if (currentUser.isAdmin()) {
            page = orderRepository.findAllByStatus(status, pageable);
        } else if ("buyer".equalsIgnoreCase(role)) {
            page = orderRepository.findByBuyerId(currentUser.id(), status, pageable);
        } else if ("seller".equalsIgnoreCase(role)) {
            page = orderRepository.findBySellerId(currentUser.id(), status, pageable);
        } else {
            page = orderRepository.findByBuyerOrSeller(currentUser.id(), status, pageable);
        }

        return PageResponse.from(page, order -> mapToOrderResponse(order, currentUser));
    }

    @Override
    @Transactional(readOnly = true)
    public OrderDetailResponse getOrderById(CurrentUser currentUser, Long id) {
        // Thứ tự kiểm tra: tồn tại -> quyền -> trạng thái
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new AppException("NOT_FOUND", "Order not found with id: " + id, HttpStatus.NOT_FOUND));

        orderAccessPolicy.requireViewer(order, currentUser);

        return mapToOrderDetailResponse(order, currentUser);
    }

    @Override
    @Transactional
    public OrderDetailResponse updateShippingInfo(CurrentUser currentUser, Long id, ShippingInfoRequest request) {
        // Bước 1: Khóa Order bằng findByIdForUpdate là lệnh đầu tiên trong transaction
        Order order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AppException("NOT_FOUND", "Order not found with id: " + id, HttpStatus.NOT_FOUND));

        // Bước 2: Kiểm tra quyền (chỉ buyer)
        orderAccessPolicy.requireBuyer(order, currentUser);

        // Bước 3: Kiểm tra trạng thái
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new AppException("ORDER_CANCELLED", "Order has been cancelled", HttpStatus.CONFLICT);
        }

        if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            // Hợp lệ
        } else if (order.getStatus() == OrderStatus.PAID) {
            // Chỉ cho phép khi Shipment chưa PICKED_UP (D-04)
            Shipment shipment = shipmentRepository.findByOrderId(order.getId()).orElse(null);
            if (shipment != null && shipment.getStatus() != ShipmentStatus.CREATED) {
                throw new AppException("INVALID_STATUS_TRANSITION",
                        "Cannot update shipping info after shipment is picked up", HttpStatus.CONFLICT);
            }
        } else {
            throw new AppException("INVALID_STATUS_TRANSITION",
                    "Cannot update shipping info when order is " + order.getStatus(), HttpStatus.CONFLICT);
        }

        order.setShippingAddress(request.getShippingAddress());
        order.setPhoneNumber(request.getPhoneNumber());
        Order saved = orderRepository.save(order);

        return mapToOrderDetailResponse(saved, currentUser);
    }

    @Override
    public void sendPaymentReminders() {
        LocalDateTime now = LocalDateTime.now(clock);

        List<Order> orders24h = orderRepository.findOrdersFor24hReminder(now.plusHours(24), now);
        for (Order o : orders24h) {
            try {
                orderLifecycleSupport.sendReminder24h(o.getId(), now);
            } catch (Exception ex) {
                log.error("Failed to send 24h reminder for order {}", o.getId(), ex);
            }
        }

        List<Order> orders2h = orderRepository.findOrdersFor2hReminder(now.plusHours(2), now);
        for (Order o : orders2h) {
            try {
                orderLifecycleSupport.sendReminder2h(o.getId(), now);
            } catch (Exception ex) {
                log.error("Failed to send 2h reminder for order {}", o.getId(), ex);
            }
        }
    }

    @Override
    public void expireOverdueOrders() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Order> overdueOrders = orderRepository.findByStatusAndPaymentDeadlineLessThanEqual(
                OrderStatus.PENDING_PAYMENT, now
        );

        for (Order o : overdueOrders) {
            try {
                orderLifecycleSupport.expireOne(o.getId(), now);
            } catch (Exception ex) {
                log.error("Failed to expire order {}", o.getId(), ex);
            }
        }
    }

    private OrderResponse mapToOrderResponse(Order order, CurrentUser currentUser) {
        String productTitle = null;
        String productImageUrl = null;

        if (productRepository != null) {
            Product product = productRepository.findById(order.getProductId()).orElse(null);
            if (product != null) {
                productTitle = product.getTitle();
            }
        }

        if (productImageRepository != null) {
            ProductImage primaryImg = productImageRepository.findByProductIdAndIsPrimaryTrue(order.getProductId())
                    .orElse(null);
            if (primaryImg != null) {
                productImageUrl = primaryImg.getImageUrl();
            }
        }

        String viewerRole = determineViewerRole(order, currentUser);

        return OrderResponse.builder()
                .id(order.getId())
                .auctionId(order.getAuctionId())
                .productId(order.getProductId())
                .productTitle(productTitle)
                .productImageUrl(productImageUrl)
                .finalPrice(order.getFinalPrice())
                .status(order.getStatus())
                .viewerRole(viewerRole)
                .paymentDeadline(order.getPaymentDeadline())
                .createdAt(order.getCreatedAt())
                .build();
    }

    private OrderDetailResponse mapToOrderDetailResponse(Order order, CurrentUser currentUser) {
        String productTitle = null;
        String productImageUrl = null;
        String sellerName = null;
        String buyerName = null;

        if (productRepository != null) {
            Product product = productRepository.findById(order.getProductId()).orElse(null);
            if (product != null) {
                productTitle = product.getTitle();
            }
        }

        if (productImageRepository != null) {
            ProductImage primaryImg = productImageRepository.findByProductIdAndIsPrimaryTrue(order.getProductId())
                    .orElse(null);
            if (primaryImg != null) {
                productImageUrl = primaryImg.getImageUrl();
            }
        }

        if (userRepository != null) {
            userRepository.findById(order.getSellerId()).ifPresent(s -> {
            });
            User seller = userRepository.findById(order.getSellerId()).orElse(null);
            if (seller != null) {
                sellerName = MaskingUtils.maskName(seller.getFullName());
            }
            User buyer = userRepository.findById(order.getBuyerId()).orElse(null);
            if (buyer != null) {
                buyerName = MaskingUtils.maskName(buyer.getFullName());
            }
        }

        OrderDetailResponse.PaymentSummaryDto latestPaymentDto = null;
        if (paymentRepository != null) {
            Payment latestPayment = paymentRepository.findTopByOrderIdOrderByAttemptNoDesc(order.getId()).orElse(null);
            if (latestPayment != null) {
                latestPaymentDto = OrderDetailResponse.PaymentSummaryDto.builder()
                        .paymentId(latestPayment.getId())
                        .attemptNo(latestPayment.getAttemptNo())
                        .status(latestPayment.getStatus())
                        .failureReason(latestPayment.getFailureReason())
                        .build();
            }
        }

        OrderDetailResponse.ShipmentSummaryDto shipmentDto = null;
        Shipment shipment = null;
        if (shipmentRepository != null) {
            shipment = shipmentRepository.findByOrderId(order.getId()).orElse(null);
            if (shipment != null) {
                shipmentDto = OrderDetailResponse.ShipmentSummaryDto.builder()
                        .id(shipment.getId())
                        .trackingCode(shipment.getTrackingCode())
                        .status(shipment.getStatus())
                        .build();
            }
        }

        boolean hasPendingComplaint = false;
        if (reportRepository != null) {
            hasPendingComplaint = reportRepository.existsByOrderIdAndReportTypeAndStatus(
                    order.getId(), ReportType.COMPLAINT, ReportStatus.PENDING
            );
        }

        String viewerRole = determineViewerRole(order, currentUser);
        List<String> allowedActions = calculateAllowedActions(order, viewerRole, shipment, hasPendingComplaint);

        return OrderDetailResponse.builder()
                .id(order.getId())
                .auctionId(order.getAuctionId())
                .productId(order.getProductId())
                .productTitle(productTitle)
                .productImageUrl(productImageUrl)
                .sellerId(order.getSellerId())
                .sellerName(sellerName)
                .buyerId(order.getBuyerId())
                .buyerName(buyerName)
                .finalPrice(order.getFinalPrice())
                .status(order.getStatus())
                .shippingAddress(order.getShippingAddress())
                .phoneNumber(order.getPhoneNumber())
                .paymentDeadline(order.getPaymentDeadline())
                .paidAt(order.getPaidAt())
                .deliveredAt(order.getDeliveredAt())
                .confirmDeadline(order.getConfirmDeadline())
                .completedAt(order.getCompletedAt())
                .cancelledAt(order.getCancelledAt())
                .cancelReason(order.getCancelReason())
                .latestPayment(latestPaymentDto)
                .shipment(shipmentDto)
                .hasPendingComplaint(hasPendingComplaint)
                .viewerRole(viewerRole)
                .allowedActions(allowedActions)
                .build();
    }

    private String determineViewerRole(Order order, CurrentUser currentUser) {
        if (currentUser == null) return "BUYER";
        if (currentUser.id().equals(order.getBuyerId())) {
            return "BUYER";
        }
        if (currentUser.id().equals(order.getSellerId())) {
            return "SELLER";
        }
        return "ADMIN";
    }

    @Override
    @Transactional
    public com.omnibid.dto.order.OrderConfirmReceiptResponse confirmReceipt(CurrentUser currentUser, Long id) {
        // REPEATABLE READ snapshot isolation: Phải khóa Order (SELECT ... FOR UPDATE)
        // là lệnh đầu tiên trong transaction để không tạo snapshot cũ trước khi khóa.
        Order order = orderRepository.findByIdForUpdate(id).orElseThrow(
                () -> new AppException("NOT_FOUND", "Không tìm thấy đơn hàng", HttpStatus.NOT_FOUND)
        );

        if (!order.getBuyerId().equals(currentUser.id())) {
            throw new AppException("FORBIDDEN", "Chỉ người mua mới được phép xác nhận nhận hàng", HttpStatus.FORBIDDEN);
        }

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new AppException("ORDER_CANCELLED", "Đơn hàng đã bị hủy", HttpStatus.CONFLICT);
        }

        if (order.getStatus() == OrderStatus.COMPLETED) {
            throw new AppException("INVALID_STATUS_TRANSITION", "Đơn hàng đã hoàn tất", HttpStatus.CONFLICT);
        }

        if (order.getStatus() != OrderStatus.SHIPPING) {
            throw new AppException("INVALID_STATUS_TRANSITION", "Đơn hàng chưa ở trạng thái đang giao hàng", HttpStatus.CONFLICT);
        }

        Shipment shipment = shipmentRepository.findByOrderId(order.getId()).orElse(null);
        if (shipment == null || shipment.getStatus() != ShipmentStatus.DELIVERED) {
            throw new AppException("INVALID_STATUS_TRANSITION", "Vận đơn chưa ở trạng thái DELIVERED", HttpStatus.CONFLICT);
        }

        if (reportRepository.existsByOrderIdAndReportTypeAndStatus(order.getId(), ReportType.COMPLAINT, ReportStatus.PENDING)) {
            throw new AppException("COMPLAINT_PENDING", "Đơn hàng đang có khiếu nại chờ xử lý", HttpStatus.CONFLICT);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        order.confirmReceived(now);
        orderRepository.save(order);

        Product product = productRepository.findByIdForUpdate(order.getProductId())
                .or(() -> productRepository.findById(order.getProductId()))
                .orElse(null);
        if (product != null) {
            product.setStatus(ProductStatus.SOLD);
            productRepository.save(product);
        }

        if (notificationService != null) {
            notificationService.notify(
                    order.getSellerId(),
                    com.omnibid.enums.NotificationType.ORDER_UPDATE,
                    com.omnibid.common.NotificationTemplates.orderReceiptConfirmedTitle(),
                    com.omnibid.common.NotificationTemplates.orderReceiptConfirmedContent(order.getId()),
                    "ORDER",
                    order.getId()
            );
        }

        return com.omnibid.dto.order.OrderConfirmReceiptResponse.builder()
                .id(order.getId())
                .orderStatus(order.getStatus())
                .productStatus(product != null ? product.getStatus() : ProductStatus.SOLD)
                .build();
    }

    @Override
    public void autoCompleteOrders() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Order> overdueOrders = orderRepository.findByStatusAndConfirmDeadlineLessThanEqual(OrderStatus.SHIPPING, now);
        for (Order o : overdueOrders) {
            try {
                orderLifecycleSupport.autoCompleteOne(o.getId(), now);
            } catch (Exception ex) {
                log.error("Failed to auto-complete order id={}", o.getId(), ex);
            }
        }
    }

    private List<String> calculateAllowedActions(Order order, String viewerRole, Shipment shipment, boolean hasPendingComplaint) {
        List<String> actions = new ArrayList<>();
        if ("BUYER".equals(viewerRole)) {
            if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
                actions.add("PAY");
                actions.add("UPDATE_SHIPPING_INFO");
            } else if (order.getStatus() == OrderStatus.PAID) {
                if (shipment == null || shipment.getStatus() == ShipmentStatus.CREATED) {
                    actions.add("UPDATE_SHIPPING_INFO");
                }
                if (!hasPendingComplaint) {
                    actions.add("COMPLAIN");
                }
            } else if (order.getStatus() == OrderStatus.SHIPPING) {
                if (shipment != null && shipment.getStatus() == ShipmentStatus.DELIVERED) {
                    if (!hasPendingComplaint) {
                        actions.add("CONFIRM_RECEIPT");
                        actions.add("COMPLAIN");
                    }
                } else if (!hasPendingComplaint) {
                    actions.add("COMPLAIN");
                }
            }
        } else if ("SELLER".equals(viewerRole)) {
            if (order.getStatus() == OrderStatus.PAID || order.getStatus() == OrderStatus.SHIPPING) {
                actions.add("UPDATE_SHIPMENT");
            }
        }
        return actions;
    }
}
