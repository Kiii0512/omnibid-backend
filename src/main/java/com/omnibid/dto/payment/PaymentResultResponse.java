package com.omnibid.dto.payment;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.omnibid.enums.OrderStatus;
import com.omnibid.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResultResponse {
    private Long paymentId;
    private Integer attemptNo;
    private PaymentStatus status;
    private OrderStatus orderStatus;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String failureReason;
}
