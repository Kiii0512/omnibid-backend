package com.omnibid.dto.auction;

import com.omnibid.enums.AuctionStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuctionFilterRequest {

    @Size(max = 255, message = "Search keyword must not exceed 255 characters")
    private String q;

    private List<Long> categoryIds;

    private AuctionStatus status;

    @DecimalMin(value = "0.00", message = "Minimum price must be non-negative")
    private BigDecimal minPrice;

    @DecimalMin(value = "0.00", message = "Maximum price must be non-negative")
    private BigDecimal maxPrice;

    private Long sellerId;

    private String sortBy;

    @AssertTrue(message = "Minimum price must not exceed maximum price")
    public boolean isPriceRangeValid() {
        if (minPrice == null || maxPrice == null) {
            return true;
        }
        return minPrice.compareTo(maxPrice) <= 0;
    }
}

