package com.omnibid.entity;

import com.omnibid.common.AppException;
import com.omnibid.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "auction_id", nullable = false, unique = true)
    private Long auctionId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "buyer_id", nullable = false)
    private Long buyerId;

    @Column(name = "final_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal finalPrice;

    @Column(name = "shipping_address")
    private String shippingAddress;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING_PAYMENT;

    @Column(name = "payment_deadline", nullable = false)
    private LocalDateTime paymentDeadline;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "confirm_deadline")
    private LocalDateTime confirmDeadline;

    @Column(name = "reminder_24h_sent_at")
    private LocalDateTime reminder24hSentAt;

    @Column(name = "reminder_2h_sent_at")
    private LocalDateTime reminder2hSentAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public void markPaid(LocalDateTime now) {
        require(status == OrderStatus.PENDING_PAYMENT, "markPaid");
        this.status = OrderStatus.PAID;
        this.paidAt = now;
    }

    public void markShipping() {
        require(status == OrderStatus.PAID, "markShipping");
        this.status = OrderStatus.SHIPPING;
    }

    public void markDelivered(LocalDateTime now, LocalDateTime confirmDeadline) {
        require(status == OrderStatus.SHIPPING, "markDelivered");
        this.deliveredAt = now;
        this.confirmDeadline = confirmDeadline;
    }

    public void confirmReceived(LocalDateTime now) {
        require(status == OrderStatus.SHIPPING, "confirmReceived");
        this.status = OrderStatus.COMPLETED;
        this.completedAt = now;
    }

    public void autoComplete(LocalDateTime now) {
        require(status == OrderStatus.SHIPPING, "autoComplete");
        this.status = OrderStatus.COMPLETED;
        this.completedAt = now;
    }

    public void expire() {
        require(status == OrderStatus.PENDING_PAYMENT, "expire");
        this.status = OrderStatus.EXPIRED;
    }

    public void cancel(String reason, LocalDateTime now) {
        require(status == OrderStatus.PENDING_PAYMENT || status == OrderStatus.PAID || status == OrderStatus.SHIPPING, "cancel");
        this.status = OrderStatus.CANCELLED;
        this.cancelReason = reason;
        this.cancelledAt = now;
    }

    private void require(boolean condition, String operation) {
        if (!condition) {
            throw new AppException("INVALID_STATUS_TRANSITION",
                    "Cannot " + operation + " when order is " + status, HttpStatus.CONFLICT);
        }
    }
}
