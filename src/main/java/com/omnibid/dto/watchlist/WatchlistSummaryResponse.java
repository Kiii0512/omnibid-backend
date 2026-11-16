package com.omnibid.dto.watchlist;

public record WatchlistSummaryResponse(
        long total,
        long active,
        long scheduled,
        long ended
) {}

