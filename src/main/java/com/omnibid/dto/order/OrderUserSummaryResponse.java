package com.omnibid.dto.order;

public record OrderUserSummaryResponse(
        RoleSummary asBuyer,
        RoleSummary asSeller
) {
    public record RoleSummary(
            long total,
            long pendingPayment,
            long paid,
            long shipping,
            long completed,
            long cancelled,
            long expired
    ) {}
}

