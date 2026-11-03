package com.omnibid.gateway;

import com.omnibid.enums.PaymentMethod;
import com.omnibid.enums.SimulatedOutcome;

import java.math.BigDecimal;

public interface MockPaymentGateway {

    GatewayResult charge(GatewayRequest request);

    record GatewayRequest(String transactionRef, BigDecimal amount, PaymentMethod method, SimulatedOutcome hint) {}

    record GatewayResult(SimulatedOutcome outcome, String failureReason) {}
}
