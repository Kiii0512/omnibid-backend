package com.omnibid.support;

import com.omnibid.config.AppProperties;
import com.omnibid.service.PaymentService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.TaskScheduler;

import java.time.Clock;

@TestConfiguration(proxyBeanMethods = false)
public class TestSupportConfig {

    @Bean
    @Primary
    public MutableClock mutableClock() {
        return new MutableClock();
    }

    @Bean
    @Primary
    public CountingMockPaymentGateway countingMockPaymentGateway(AppProperties appProperties,
                                                                 ObjectProvider<PaymentService> paymentServiceProvider,
                                                                 TaskScheduler taskScheduler) {
        return new CountingMockPaymentGateway(appProperties, paymentServiceProvider, taskScheduler);
    }
}
