package com.omnibid.gateway;

import com.omnibid.enums.PaymentMethod;
import com.omnibid.enums.PaymentStatus;

import java.math.BigDecimal;

public interface MockPaymentGateway {

    GatewayResult charge(GatewayRequest request);

    record GatewayRequest(Long orderId, BigDecimal amount, PaymentMethod method, PaymentStatus hint) {}

    record GatewayResult(PaymentStatus outcome, String failureReason) {}
}
