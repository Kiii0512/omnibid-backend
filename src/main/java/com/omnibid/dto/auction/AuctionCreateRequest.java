package com.omnibid.dto.auction;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuctionCreateRequest {

    @NotNull(message = "Product ID cannot be null")
    private Long productId;

    @NotNull(message = "Start price cannot be null")
    @Positive(message = "Start price must be greater than 0")
    @Digits(integer = 15, fraction = 0, message = "Start price must be an integer VND amount")
    private BigDecimal startPrice;

    @NotNull(message = "Bid increment cannot be null")
    @Positive(message = "Bid increment must be greater than 0")
    @Digits(integer = 15, fraction = 0, message = "Bid increment must be an integer VND amount")
    private BigDecimal bidIncrement;

    @Positive(message = "Reserve price must be greater than 0")
    @Digits(integer = 15, fraction = 0, message = "Reserve price must be an integer VND amount")
    private BigDecimal reservePrice;

    @NotNull(message = "Start time cannot be null")
    private Instant startTime;

    @NotNull(message = "End time cannot be null")
    private Instant endTime;

    @AssertTrue(message = "Start time must be before end time")
    public boolean isStartTimeBeforeEndTime() {
        if (startTime == null || endTime == null) {
            return true;
        }
        return startTime.isBefore(endTime);
    }
}

