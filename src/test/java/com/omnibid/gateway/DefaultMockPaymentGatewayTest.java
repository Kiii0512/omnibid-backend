package com.omnibid.gateway;

import com.omnibid.config.AppProperties;
import com.omnibid.enums.PaymentMethod;
import com.omnibid.enums.PaymentStatus;
import com.omnibid.gateway.MockPaymentGateway.GatewayRequest;
import com.omnibid.gateway.MockPaymentGateway.GatewayResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultMockPaymentGatewayTest {

    private AppProperties appProperties;
    private DefaultMockPaymentGateway gateway;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        gateway = new DefaultMockPaymentGateway(appProperties);
    }

    @Test
    @DisplayName("simulate-enabled=false (mặc định): luôn trả SUCCESS dù hint là FAILED")
    void charge_SimulateDisabled_AlwaysReturnsSuccess() {
        assertThat(appProperties.getPayment().isSimulateEnabled()).isFalse();

        GatewayResult res = gateway.charge(new GatewayRequest(
                BigDecimal.valueOf(500000), PaymentMethod.MOCK_CARD, PaymentStatus.FAILED
        ));

        assertThat(res.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(res.failureReason()).isNull();
    }

    @Test
    @DisplayName("simulate-enabled=true: trả đúng SUCCESS hoặc FAILED (MOCK_DECLINED), null mặc định SUCCESS")
    void charge_SimulateEnabled_HonorsHint() {
        appProperties.getPayment().setSimulateEnabled(true);

        GatewayResult resSuccess = gateway.charge(new GatewayRequest(
                BigDecimal.valueOf(100000), PaymentMethod.MOCK_CARD, PaymentStatus.SUCCESS
        ));
        assertThat(resSuccess.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(resSuccess.failureReason()).isNull();

        GatewayResult resFailed = gateway.charge(new GatewayRequest(
                BigDecimal.valueOf(100000), PaymentMethod.MOCK_CARD, PaymentStatus.FAILED
        ));
        assertThat(resFailed.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(resFailed.failureReason()).isEqualTo("MOCK_DECLINED");

        GatewayResult resNull = gateway.charge(new GatewayRequest(
                BigDecimal.valueOf(100000), PaymentMethod.MOCK_CARD, null
        ));
        assertThat(resNull.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(resNull.failureReason()).isNull();
    }
}
