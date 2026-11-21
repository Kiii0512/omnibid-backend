package com.omnibid.gateway;

import com.omnibid.config.AppProperties;
import com.omnibid.enums.PaymentMethod;
import com.omnibid.enums.PaymentStatus;
import com.omnibid.gateway.MockPaymentGateway.GatewayRequest;
import com.omnibid.gateway.MockPaymentGateway.GatewayResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultMockPaymentGatewayTest {

    @Test
    @DisplayName("charge hint = SUCCESS / FAILED trả kết quả tương ứng, FAILED có failureReason = MOCK_DECLINED")
    void charge_WithHints_ReturnsExpectedOutcomes() {
        AppProperties appProperties = new AppProperties(null, null, null, new AppProperties.Payment(true), null, null);
        DefaultMockPaymentGateway gateway = new DefaultMockPaymentGateway(appProperties);

        GatewayRequest reqSuccess = new GatewayRequest(1L, BigDecimal.valueOf(100000), PaymentMethod.MOCK_CARD, PaymentStatus.SUCCESS);
        GatewayResult resSuccess = gateway.charge(reqSuccess);
        assertThat(resSuccess.outcome()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(resSuccess.failureReason()).isNull();

        GatewayRequest reqFailed = new GatewayRequest(1L, BigDecimal.valueOf(100000), PaymentMethod.MOCK_CARD, PaymentStatus.FAILED);
        GatewayResult resFailed = gateway.charge(reqFailed);
        assertThat(resFailed.outcome()).isEqualTo(PaymentStatus.FAILED);
        assertThat(resFailed.failureReason()).isEqualTo("MOCK_DECLINED");
    }

    @Test
    @DisplayName("Khi simulate-enabled = false, charge luôn trả SUCCESS bất kể hint")
    void charge_WhenSimulateDisabled_AlwaysReturnsSuccess() {
        AppProperties appProperties = new AppProperties(null, null, null, new AppProperties.Payment(false), null, null);
        DefaultMockPaymentGateway gateway = new DefaultMockPaymentGateway(appProperties);

        GatewayRequest reqFailed = new GatewayRequest(1L, BigDecimal.valueOf(100000), PaymentMethod.MOCK_CARD, PaymentStatus.FAILED);
        GatewayResult res = gateway.charge(reqFailed);
        assertThat(res.outcome()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(res.failureReason()).isNull();
    }
}
