package com.omnibid.dto.payment;

import com.omnibid.enums.PaymentMethod;
import com.omnibid.enums.PaymentStatus;
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
public class PaymentHistoryResponse {
    private Long paymentId;
    private Integer attemptNo;
    private PaymentMethod method;
    private BigDecimal amount;
    private PaymentStatus status;
    private String transactionRef;
    private String failureReason;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
}
