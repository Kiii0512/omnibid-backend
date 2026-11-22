package com.omnibid.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Order order = new Order();
    private Auction auction = new Auction();
    private Scheduler scheduler = new Scheduler();
    private Payment payment = new Payment();
    private Security security = new Security();
    private Cors cors = new Cors();

    @Getter
    @Setter
    public static class Order {
        @DurationUnit(ChronoUnit.HOURS)
        private Duration paymentDeadlineHours = Duration.ofHours(48);

        @DurationUnit(ChronoUnit.DAYS)
        private Duration confirmDeadlineDays = Duration.ofDays(7);
    }

    @Getter
    @Setter
    public static class Auction {
        @DurationUnit(ChronoUnit.MINUTES)
        private Duration minDurationMinutes = Duration.ofMinutes(60);

        @DurationUnit(ChronoUnit.DAYS)
        private Duration maxDurationDays = Duration.ofDays(30);
    }

    @Getter
    @Setter
    public static class Scheduler {
        @DurationUnit(ChronoUnit.SECONDS)
        private Duration intervalSeconds = Duration.ofSeconds(10);
    }

    @Getter
    @Setter
    public static class Payment {
        private boolean simulateEnabled = false;
    }

    @Getter
    @Setter
    public static class Security {
        private Login login = new Login();

        @Getter
        @Setter
        public static class Login {
            private int maxAttempts = 5;

            @DurationUnit(ChronoUnit.MINUTES)
            private Duration windowMinutes = Duration.ofMinutes(15);

            @DurationUnit(ChronoUnit.MINUTES)
            private Duration blockMinutes = Duration.ofMinutes(15);
        }
    }

    @Getter
    @Setter
    public static class Cors {
        private String allowedOrigins = "http://localhost:3000";
    }
}
