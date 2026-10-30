package com.omnibid.dto.auction;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuctionCreateRequest {

    @NotNull(message = "Product ID cannot be null")
    private Long productId;

    @NotNull(message = "Start price cannot be null")
    @DecimalMin(value = "0.01", message = "Start price must be greater than 0")
    private BigDecimal startPrice;

    @NotNull(message = "Bid increment cannot be null")
    @DecimalMin(value = "0.01", message = "Bid increment must be greater than 0")
    private BigDecimal bidIncrement;

    @DecimalMin(value = "0.01", message = "Reserve price must be greater than 0")
    private BigDecimal reservePrice;

    @NotNull(message = "Start time cannot be null")
    private LocalDateTime startTime;

    @NotNull(message = "End time cannot be null")
    private LocalDateTime endTime;

    @AssertTrue(message = "Start time must be before end time")
    public boolean isStartTimeBeforeEndTime() {
        if (startTime == null || endTime == null) {
            return true;
        }
        return startTime.isBefore(endTime);
    }
}

