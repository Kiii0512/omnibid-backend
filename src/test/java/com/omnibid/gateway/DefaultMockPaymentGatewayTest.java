package com.omnibid.gateway;

import com.omnibid.config.AppProperties;
import com.omnibid.enums.PaymentMethod;
import com.omnibid.enums.SimulatedOutcome;
import com.omnibid.gateway.MockPaymentGateway.GatewayRequest;
import com.omnibid.gateway.MockPaymentGateway.GatewayResult;
import com.omnibid.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultMockPaymentGatewayTest {

    @Mock
    private PaymentService paymentService;

    @Mock
    private TaskScheduler taskScheduler;

    private AppProperties appProperties;
    private DefaultMockPaymentGateway gateway;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        gateway = new DefaultMockPaymentGateway(appProperties, paymentService, taskScheduler);
    }

    @Test
    @DisplayName("UT-GW-01 hint = SUCCESS / FAILED / PROCESSING trả kết quả tương ứng, FAILED có failureReason = MOCK_DECLINED")
    void charge_WithHints_ReturnsExpectedOutcomes() {
        GatewayRequest reqSuccess = new GatewayRequest("TX-1", BigDecimal.valueOf(100000), PaymentMethod.MOCK_CARD, SimulatedOutcome.SUCCESS);
        GatewayResult resSuccess = gateway.charge(reqSuccess);
        assertThat(resSuccess.outcome()).isEqualTo(SimulatedOutcome.SUCCESS);
        assertThat(resSuccess.failureReason()).isNull();

        GatewayRequest reqFailed = new GatewayRequest("TX-2", BigDecimal.valueOf(100000), PaymentMethod.MOCK_BANK, SimulatedOutcome.FAILED);
        GatewayResult resFailed = gateway.charge(reqFailed);
        assertThat(resFailed.outcome()).isEqualTo(SimulatedOutcome.FAILED);
        assertThat(resFailed.failureReason()).isEqualTo("MOCK_DECLINED");

        GatewayRequest reqProc = new GatewayRequest("TX-3", BigDecimal.valueOf(100000), PaymentMethod.MOCK_WALLET, SimulatedOutcome.PROCESSING);
        GatewayResult resProc = gateway.charge(reqProc);
        assertThat(resProc.outcome()).isEqualTo(SimulatedOutcome.PROCESSING);
        assertThat(resProc.failureReason()).isNull();
    }

    @Test
    @DisplayName("UT-GW-02 hint = null thì dùng default-outcome từ cấu hình")
    void charge_NullHint_UsesDefaultOutcome() {
        appProperties.getPayment().getMock().setDefaultOutcome(SimulatedOutcome.FAILED);

        GatewayRequest req = new GatewayRequest("TX-DEFAULT", BigDecimal.valueOf(500000), PaymentMethod.MOCK_CARD, null);
        GatewayResult res = gateway.charge(req);

        assertThat(res.outcome()).isEqualTo(SimulatedOutcome.FAILED);
        assertThat(res.failureReason()).isEqualTo("MOCK_DECLINED");
    }

    @Test
    @DisplayName("UT-GW-03 PROCESSING lên lịch callback finalizePayment(ref, SUCCESS) sau delay")
    void charge_Processing_SchedulesCallback() {
        appProperties.getPayment().getMock().setProcessingDelaySeconds(10);
        GatewayRequest req = new GatewayRequest("TX-ASYNC", BigDecimal.valueOf(250000), PaymentMethod.MOCK_CARD, SimulatedOutcome.PROCESSING);

        GatewayResult res = gateway.charge(req);
        assertThat(res.outcome()).isEqualTo(SimulatedOutcome.PROCESSING);

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<Instant> instantCaptor = ArgumentCaptor.forClass(Instant.class);

        verify(taskScheduler).schedule(runnableCaptor.capture(), instantCaptor.capture());
        assertThat(instantCaptor.getValue()).isNotNull();

        // Run the scheduled task and verify finalizePayment is invoked
        runnableCaptor.getValue().run();
        verify(paymentService).finalizePayment("TX-ASYNC", SimulatedOutcome.SUCCESS, null);
    }
}
