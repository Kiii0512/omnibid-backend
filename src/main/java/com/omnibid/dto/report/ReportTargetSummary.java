package com.omnibid.dto.report;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.omnibid.enums.AuctionStatus;
import com.omnibid.enums.OrderStatus;
import com.omnibid.enums.ProductStatus;
import com.omnibid.enums.UserRole;
import com.omnibid.enums.UserStatus;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReportTargetSummary(
        ProductTarget product,
        UserTarget user,
        OrderTarget order
) {
    public record ActiveAuctionRef(
            Long id,
            AuctionStatus status,
            Integer bidCount
    ) {}

    public record ProductTarget(
            Long id,
            String title,
            ProductStatus status,
            Long sellerId,
            ActiveAuctionRef activeAuction
    ) {}

    public record UserTarget(
            Long id,
            String fullName,
            String email,
            UserRole role,
            UserStatus status
    ) {}

    public record OrderTarget(
            Long id,
            OrderStatus status,
            Long buyerId,
            Long sellerId,
            Long auctionId
    ) {}
}
