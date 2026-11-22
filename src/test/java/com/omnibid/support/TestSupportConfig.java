package com.omnibid.support;

import com.omnibid.config.AppProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class TestSupportConfig {

    @Bean
    @Primary
    public MutableClock mutableClock() {
        return new MutableClock();
    }

    @Bean
    @Primary
    public CountingMockPaymentGateway countingMockPaymentGateway(AppProperties appProperties) {
        return new CountingMockPaymentGateway(appProperties);
    }

    @Bean
    @Primary
    public FakeImageStorageService fakeImageStorageService() {
        return new FakeImageStorageService();
    }
}
