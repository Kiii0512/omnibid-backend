package com.omnibid.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private OrderProperties order = new OrderProperties();
    private PaymentProperties payment = new PaymentProperties();
    private SchedulerProperties scheduler = new SchedulerProperties();
    private AuctionProperties auction = new AuctionProperties();

    @Getter
    @Setter
    public static class OrderProperties {
        private int paymentDeadlineHours = 48;
        private int confirmWindowDays = 7;
        private List<Integer> reminderHours = List.of(24, 2);
    }

    @Getter
    @Setter
    public static class PaymentProperties {
        private RateLimitProperties rateLimit = new RateLimitProperties();
        private int pendingTimeoutMinutes = 5;

        @Getter
        @Setter
        public static class RateLimitProperties {
            private int max = 5;
            private int windowMinutes = 10;
        }
    }

    @Getter
    @Setter
    public static class SchedulerProperties {
        private long fixedRateMs = 10000;
    }

    @Getter
    @Setter
    public static class AuctionProperties {
        private int minDurationHours = 1;
        private int maxDurationDays = 30;
    }
}
