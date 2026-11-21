package com.omnibid.dto.bid;

import java.math.BigDecimal;
import java.time.Instant;

public record BidHistoryItem(
        Long id,
        BigDecimal amount,
        String bidderName,
        Instant createdAt
) {}
