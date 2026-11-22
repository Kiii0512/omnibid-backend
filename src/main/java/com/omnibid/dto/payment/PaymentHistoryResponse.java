package com.omnibid.dto.payment;

import com.omnibid.enums.PaymentMethod;
import com.omnibid.enums.PaymentStatus;
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
public class PaymentHistoryResponse {
    private Long id;
    private Integer attemptNo;
    private BigDecimal amount;
    private PaymentMethod method;
    private PaymentStatus status;
    private String failureReason;
    private Instant createdAt;
    private Instant paidAt;
}
