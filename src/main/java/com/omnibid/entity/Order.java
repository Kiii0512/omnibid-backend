package com.omnibid.entity;

import com.omnibid.common.BusinessException;
import com.omnibid.common.ErrorCode;
import com.omnibid.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

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

    @Column(name = "final_price", nullable = false, precision = 15, scale = 0)
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
    private Instant paymentDeadline;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "tracking_code", length = 50)
    private String trackingCode;

    @Column(name = "shipped_at")
    private Instant shippedAt;

    @Column(name = "confirm_deadline")
    private Instant confirmDeadline;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public void markPaid(Instant now) {
        require(this.status == OrderStatus.PENDING_PAYMENT, "markPaid");
        this.status = OrderStatus.PAID;
        this.paidAt = now;
    }

    public void markShipped(String trackingCode, Instant now, Duration confirmWindow) {
        require(this.status == OrderStatus.PAID, "markShipped");
        this.status = OrderStatus.SHIPPING;
        this.trackingCode = trackingCode;
        this.shippedAt = now;
        this.confirmDeadline = now.plus(confirmWindow);
    }

    public void confirmReceived(Instant now) {
        require(this.status == OrderStatus.SHIPPING, "confirmReceived");
        this.status = OrderStatus.COMPLETED;
        this.completedAt = now;
    }

    public void autoComplete(Instant now) {
        require(this.status == OrderStatus.SHIPPING, "autoComplete");
        this.status = OrderStatus.COMPLETED;
        this.completedAt = now;
    }

    public void expire(Instant now) {
        require(this.status == OrderStatus.PENDING_PAYMENT, "expire");
        this.status = OrderStatus.EXPIRED;
    }

    public void cancel(String reason, Instant now) {
        require(this.status == OrderStatus.PENDING_PAYMENT, "cancel");
        this.status = OrderStatus.CANCELLED;
        this.cancelReason = reason;
        this.cancelledAt = now;
    }

    public boolean isPaymentOverdue(Instant now) {
        return this.paymentDeadline != null && !now.isBefore(this.paymentDeadline);
    }

    private void require(boolean condition, String operation) {
        if (!condition) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATUS_TRANSITION,
                    "Cannot " + operation + " when order is " + this.status
            );
        }
    }
}
