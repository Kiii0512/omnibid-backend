package com.omnibid.entity;

import com.omnibid.common.BusinessException;
import com.omnibid.common.ErrorCode;
import com.omnibid.enums.AuctionStatus;
import com.omnibid.enums.ReserveStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "auctions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Auction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "start_price", nullable = false, precision = 15, scale = 0)
    private BigDecimal startPrice;

    @Column(name = "bid_increment", nullable = false, precision = 15, scale = 0)
    private BigDecimal bidIncrement;

    @Column(name = "reserve_price", precision = 15, scale = 0)
    private BigDecimal reservePrice;

    @Column(name = "current_price", nullable = false, precision = 15, scale = 0)
    private BigDecimal currentPrice;

    @Column(name = "highest_bidder_id")
    private Long highestBidderId;

    @Column(name = "bid_count", nullable = false)
    @Builder.Default
    private Integer bidCount = 0;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AuctionStatus status = AuctionStatus.SCHEDULED;

    @Column(name = "winner_id")
    private Long winnerId;

    @Column(name = "final_price", precision = 15, scale = 0)
    private BigDecimal finalPrice;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public boolean isOpen() {
        return this.status == AuctionStatus.SCHEDULED || this.status == AuctionStatus.ACTIVE;
    }

    public BigDecimal minNextBid() {
        if (this.bidCount == null || this.bidCount == 0) {
            return this.startPrice;
        }
        return this.currentPrice.add(this.bidIncrement);
    }

    public ReserveStatus reserveStatus() {
        if (this.reservePrice == null) {
            return ReserveStatus.NO_RESERVE;
        }
        if (this.bidCount != null && this.bidCount > 0 && this.currentPrice.compareTo(this.reservePrice) >= 0) {
            return ReserveStatus.MET;
        }
        return ReserveStatus.NOT_MET;
    }

    public void activate(Instant now) {
        if (this.status != AuctionStatus.SCHEDULED || now.isBefore(this.startTime)) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATUS_TRANSITION,
                    "Cannot activate auction when status is " + this.status
            );
        }
        this.status = AuctionStatus.ACTIVE;
    }

    public Bid placeBid(Long bidderId, BigDecimal amount, Instant now) {
        if (this.status != AuctionStatus.ACTIVE || !now.isBefore(this.endTime)) {
            throw new BusinessException(ErrorCode.AUCTION_NOT_ACTIVE, "Auction is not active for bidding");
        }
        if (this.sellerId.equals(bidderId)) {
            throw new BusinessException(ErrorCode.SELF_BID_NOT_ALLOWED, "Seller cannot bid on own auction");
        }
        if (this.highestBidderId != null && this.highestBidderId.equals(bidderId)) {
            throw new BusinessException(ErrorCode.ALREADY_LEADING, "You are already the highest bidder");
        }
        BigDecimal minNext = minNextBid();
        if (amount == null || amount.compareTo(minNext) < 0) {
            throw new BusinessException(
                    ErrorCode.BID_TOO_LOW,
                    "Bid amount must be at least " + minNext,
                    Map.of("minNextBid", minNext)
            );
        }
        this.currentPrice = amount;
        this.highestBidderId = bidderId;
        this.bidCount = (this.bidCount == null ? 0 : this.bidCount) + 1;
        return Bid.builder()
                .auctionId(this.id)
                .bidderId(bidderId)
                .amount(amount)
                .build();
    }

    public void closeAsEnded(Instant now) {
        if (this.status != AuctionStatus.ACTIVE) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATUS_TRANSITION,
                    "Cannot closeAsEnded when auction is " + this.status
            );
        }
        this.status = AuctionStatus.ENDED;
        this.winnerId = this.highestBidderId;
        this.finalPrice = this.currentPrice;
        this.closedAt = now;
    }

    public void closeAsUnsold(Instant now) {
        if (this.status != AuctionStatus.ACTIVE) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATUS_TRANSITION,
                    "Cannot closeAsUnsold when auction is " + this.status
            );
        }
        this.status = AuctionStatus.UNSOLD;
        this.winnerId = null;
        this.finalPrice = null;
        this.closedAt = now;
    }

    public void cancel(String reason) {
        if (!isOpen()) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATUS_TRANSITION,
                    "Cannot cancel auction when status is " + this.status
            );
        }
        this.status = AuctionStatus.CANCELLED;
        this.cancelReason = reason;
    }
}
