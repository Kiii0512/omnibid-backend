package com.omnibid.entity;

import com.omnibid.common.BusinessException;
import com.omnibid.common.ErrorCode;
import com.omnibid.enums.ProductCondition;
import com.omnibid.enums.ProductStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_condition", nullable = false)
    private ProductCondition condition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ProductStatus status = ProductStatus.AVAILABLE;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public void markInAuction() {
        require(status == ProductStatus.AVAILABLE, "tạo phiên");
        this.status = ProductStatus.IN_AUCTION;
    }

    public void reserve() {
        require(status == ProductStatus.IN_AUCTION, "giữ hàng");
        this.status = ProductStatus.RESERVED;
    }

    public void release() {
        if (this.status != ProductStatus.HIDDEN) {
            this.status = ProductStatus.AVAILABLE;
        }
    }

    public void markSold() {
        if (this.status != ProductStatus.HIDDEN) {
            this.status = ProductStatus.SOLD;
        }
    }

    public void hide() {
        this.status = ProductStatus.HIDDEN;
    }

    public boolean isEditable() {
        return this.status == ProductStatus.AVAILABLE;
    }

    private void require(boolean condition, String operation) {
        if (!condition) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATUS_TRANSITION,
                    "Không thể " + operation + " khi sản phẩm ở trạng thái " + status
            );
        }
    }
}
