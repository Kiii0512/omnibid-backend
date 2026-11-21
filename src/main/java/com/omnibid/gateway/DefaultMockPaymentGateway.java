package com.omnibid.gateway;

import com.omnibid.config.AppProperties;
import com.omnibid.enums.PaymentStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DefaultMockPaymentGateway implements MockPaymentGateway {

    private final AppProperties.Payment paymentProperties;

    public DefaultMockPaymentGateway(AppProperties appProperties) {
        this.paymentProperties = appProperties.getPayment();
    }

    @Override
    public GatewayResult charge(GatewayRequest request) {
        // Nếu simulate-enabled tắt, luôn SUCCESS (v4 BR-12)
        if (paymentProperties != null && !paymentProperties.isSimulateEnabled()) {
            return new GatewayResult(PaymentStatus.SUCCESS, null);
        }

        PaymentStatus outcome = request.hint();
        if (outcome == null) {
            outcome = PaymentStatus.SUCCESS;
        }

        if (outcome == PaymentStatus.FAILED) {
            return new GatewayResult(PaymentStatus.FAILED, "Thanh toán bị từ chối bởi cổng thanh toán mô phỏng");
        }
        return new GatewayResult(PaymentStatus.SUCCESS, null);
    }
}
