package com.omnibid.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = AppPropertiesTest.TestConfig.class)
@TestPropertySource(properties = {
        "app.order.payment-deadline-hours=48",
        "app.order.confirm-deadline-days=7",
        "app.auction.min-duration-minutes=60",
        "app.auction.max-duration-days=30",
        "app.scheduler.interval-seconds=10",
        "app.payment.simulate-enabled=false",
        "app.security.login.max-attempts=5",
        "app.security.login.window-minutes=15",
        "app.security.login.block-minutes=15",
        "app.cors.allowed-origins=http://localhost:3000"
})
class AppPropertiesTest {

    @EnableConfigurationProperties(AppProperties.class)
    static class TestConfig {}

    @Autowired
    private AppProperties appProperties;

    @Test
    @DisplayName("EnableConfigurationProperties(AppProperties.class) binds all app.* properties successfully")
    void testAppPropertiesBinding() {
        assertThat(appProperties).isNotNull();

        assertThat(appProperties.order()).isNotNull();
        assertThat(appProperties.order().getPaymentDeadlineHours()).isEqualTo(48);
        assertThat(appProperties.order().getConfirmDeadlineDays()).isEqualTo(7);

        assertThat(appProperties.auction()).isNotNull();
        assertThat(appProperties.auction().getMinDurationMinutes()).isEqualTo(60);
        assertThat(appProperties.auction().getMaxDurationDays()).isEqualTo(30);

        assertThat(appProperties.scheduler()).isNotNull();
        assertThat(appProperties.scheduler().getIntervalSeconds()).isEqualTo(10);

        assertThat(appProperties.payment()).isNotNull();
        assertThat(appProperties.payment().isSimulateEnabled()).isFalse();

        assertThat(appProperties.security()).isNotNull();
        assertThat(appProperties.security().login()).isNotNull();
        assertThat(appProperties.security().login().maxAttempts()).isEqualTo(5);
        assertThat(appProperties.security().login().windowMinutes()).isEqualTo(Duration.ofMinutes(15));
        assertThat(appProperties.security().login().blockMinutes()).isEqualTo(Duration.ofMinutes(15));

        assertThat(appProperties.cors()).isNotNull();
        assertThat(appProperties.cors().allowedOrigins()).isEqualTo("http://localhost:3000");
    }
}
