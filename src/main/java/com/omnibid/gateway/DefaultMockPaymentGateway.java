package com.omnibid.gateway;

import com.omnibid.config.AppProperties;
import com.omnibid.enums.SimulatedOutcome;
import com.omnibid.service.PaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
public class DefaultMockPaymentGateway implements MockPaymentGateway {

    private final AppProperties appProperties;
    private final ObjectProvider<PaymentService> paymentServiceProvider;
    private final TaskScheduler taskScheduler;
    private PaymentService directPaymentService;

    public DefaultMockPaymentGateway(AppProperties appProperties,
                                     ObjectProvider<PaymentService> paymentServiceProvider,
                                     TaskScheduler taskScheduler) {
        this.appProperties = appProperties;
        this.paymentServiceProvider = paymentServiceProvider;
        this.taskScheduler = taskScheduler;
    }

    public DefaultMockPaymentGateway(AppProperties appProperties,
                                     PaymentService paymentService,
                                     TaskScheduler taskScheduler) {
        this.appProperties = appProperties;
        this.paymentServiceProvider = null;
        this.directPaymentService = paymentService;
        this.taskScheduler = taskScheduler;
    }

    private PaymentService getPaymentService() {
        if (directPaymentService != null) {
            return directPaymentService;
        }
        return paymentServiceProvider != null ? paymentServiceProvider.getIfAvailable() : null;
    }

    @Override
    public GatewayResult charge(GatewayRequest request) {
        SimulatedOutcome outcome = request.hint();
        if (outcome == null) {
            outcome = appProperties.getPayment().getMock().getDefaultOutcome();
        }

        switch (outcome) {
            case SUCCESS:
                return new GatewayResult(SimulatedOutcome.SUCCESS, null);
            case FAILED:
                return new GatewayResult(SimulatedOutcome.FAILED, "MOCK_DECLINED");
            case PROCESSING:
                scheduleDelayedFinalization(request.transactionRef());
                return new GatewayResult(SimulatedOutcome.PROCESSING, null);
            default:
                return new GatewayResult(SimulatedOutcome.SUCCESS, null);
        }
    }

    private void scheduleDelayedFinalization(String transactionRef) {
        int delaySeconds = appProperties.getPayment().getMock().getProcessingDelaySeconds();
        Instant triggerTime = Instant.now().plusSeconds(delaySeconds);
        log.info("Scheduling delayed finalization for txRef={} in {}s", transactionRef, delaySeconds);
        if (taskScheduler != null) {
            taskScheduler.schedule(() -> {
                try {
                    PaymentService ps = getPaymentService();
                    if (ps != null) {
                        ps.finalizePayment(transactionRef, SimulatedOutcome.SUCCESS, null);
                    } else {
                        log.warn("PaymentService not available for delayed finalization of txRef={}", transactionRef);
                    }
                } catch (Exception e) {
                    log.error("Failed to execute delayed finalization for txRef={}", transactionRef, e);
                }
            }, triggerTime);
        }
    }
}
