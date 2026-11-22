package com.omnibid.gateway;

import com.omnibid.config.AppProperties;
import com.omnibid.enums.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DefaultMockPaymentGateway implements MockPaymentGateway {

    private final AppProperties appProperties;

    @Override
    public GatewayResult charge(GatewayRequest request) {
        PaymentStatus outcome = request.hint();
        if (outcome == null || !appProperties.getPayment().isSimulateEnabled()) {
            outcome = PaymentStatus.SUCCESS;
        }
        if (outcome == PaymentStatus.FAILED) {
            return new GatewayResult(PaymentStatus.FAILED, "MOCK_DECLINED");
        }
        return new GatewayResult(PaymentStatus.SUCCESS, null);
    }
}
