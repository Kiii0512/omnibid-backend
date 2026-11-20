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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentResponse {
    private Long paymentId;
    private Integer attemptNo;
    private PaymentStatus status;
    private OrderStatus orderStatus;
    private String failureReason;
    private ShipmentSummaryDto shipment;
}
