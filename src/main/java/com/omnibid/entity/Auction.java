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

    public void activate(Instant now) {
        require(status == AuctionStatus.SCHEDULED, "kích hoạt phiên");
        this.status = AuctionStatus.ACTIVE;
    }

    public Bid placeBid(Long bidderId, BigDecimal amount, Instant now) {
        if (status != AuctionStatus.ACTIVE || (now != null && !now.isBefore(endTime))) {
            throw new BusinessException(ErrorCode.AUCTION_NOT_ACTIVE, "Phiên đấu giá hiện không trong trạng thái hoạt động hoặc đã hết giờ");
        }
        if (bidderId.equals(sellerId)) {
            throw new BusinessException(ErrorCode.SELF_BID_NOT_ALLOWED, "Người bán không được phép đặt giá trên phiên của chính mình");
        }
        if (highestBidderId != null && bidderId.equals(highestBidderId)) {
            throw new BusinessException(ErrorCode.ALREADY_LEADING, "Bạn đang là người đặt giá cao nhất");
        }
        BigDecimal minNext = minNextBid();
        if (amount == null || amount.compareTo(minNext) < 0) {
            throw new BusinessException(ErrorCode.BID_TOO_LOW, "Giá đặt thấp hơn mức tối thiểu yêu cầu", Map.of("minNextBid", minNext));
        }

        this.currentPrice = amount;
        this.highestBidderId = bidderId;
        this.bidCount = (this.bidCount == null ? 0 : this.bidCount) + 1;

        return Bid.builder()
                .auctionId(this.id)
                .bidderId(bidderId)
                .amount(amount)
                .createdAt(now != null ? now : Instant.now())
                .build();
    }

    public BigDecimal minNextBid() {
        if (bidCount == null || bidCount == 0) {
            return startPrice;
        }
        return currentPrice.add(bidIncrement);
    }

    public ReserveStatus reserveStatus() {
        if (reservePrice == null) {
            return ReserveStatus.NO_RESERVE;
        }
        if (currentPrice != null && currentPrice.compareTo(reservePrice) >= 0) {
            return ReserveStatus.MET;
        }
        return ReserveStatus.NOT_MET;
    }

    public void closeAsEnded(Instant now) {
        require(status == AuctionStatus.ACTIVE, "chốt phiên thành công");
        this.status = AuctionStatus.ENDED;
        this.closedAt = now;
        this.winnerId = this.highestBidderId;
        this.finalPrice = this.currentPrice;
    }

    public void closeAsUnsold(Instant now) {
        require(status == AuctionStatus.ACTIVE, "chốt phiên không người thắng");
        this.status = AuctionStatus.UNSOLD;
        this.closedAt = now;
    }

    public void cancel(String reason) {
        require(status == AuctionStatus.SCHEDULED || status == AuctionStatus.ACTIVE, "hủy phiên");
        this.status = AuctionStatus.CANCELLED;
        this.cancelReason = reason;
    }

    public boolean isOpen() {
        return status == AuctionStatus.SCHEDULED || status == AuctionStatus.ACTIVE;
    }

    private void require(boolean condition, String operation) {
        if (!condition) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATUS_TRANSITION,
                    "Không thể " + operation + " khi phiên ở trạng thái " + status
            );
        }
    }
}
