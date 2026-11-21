package com.omnibid.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Order order,
        Auction auction,
        Scheduler scheduler,
        Payment payment,
        Security security,
        Cors cors
) {
    public AppProperties {
        if (order == null) order = new Order(Duration.ofHours(48), Duration.ofDays(7));
        if (auction == null) auction = new Auction(Duration.ofMinutes(60), Duration.ofDays(30));
        if (scheduler == null) scheduler = new Scheduler(Duration.ofSeconds(10));
        if (payment == null) payment = new Payment(false);
        if (security == null) security = new Security(new Security.Login(5, Duration.ofMinutes(15), Duration.ofMinutes(15)));
        if (cors == null) cors = new Cors("http://localhost:3000");
    }

    public AppProperties() {
        this(null, null, null, null, null, null);
    }

    public record Order(
            @DurationUnit(ChronoUnit.HOURS) Duration paymentDeadlineHours,
            @DurationUnit(ChronoUnit.DAYS) Duration confirmDeadlineDays
    ) {
        public Order {
            if (paymentDeadlineHours == null) paymentDeadlineHours = Duration.ofHours(48);
            if (confirmDeadlineDays == null) confirmDeadlineDays = Duration.ofDays(7);
        }

        public int getPaymentDeadlineHours() {
            return (int) paymentDeadlineHours.toHours();
        }

        public int getConfirmDeadlineDays() {
            return (int) confirmDeadlineDays.toDays();
        }

        public int getConfirmWindowDays() {
            return (int) confirmDeadlineDays.toDays();
        }
    }

    public record Auction(
            @DurationUnit(ChronoUnit.MINUTES) Duration minDurationMinutes,
            @DurationUnit(ChronoUnit.DAYS) Duration maxDurationDays
    ) {
        public Auction {
            if (minDurationMinutes == null) minDurationMinutes = Duration.ofMinutes(60);
            if (maxDurationDays == null) maxDurationDays = Duration.ofDays(30);
        }

        public int getMinDurationMinutes() {
            return (int) minDurationMinutes.toMinutes();
        }

        public int getMinDurationHours() {
            return (int) minDurationMinutes.toHours();
        }

        public int getMaxDurationDays() {
            return (int) maxDurationDays.toDays();
        }
    }

    public record Scheduler(
            @DurationUnit(ChronoUnit.SECONDS) Duration intervalSeconds
    ) {
        public Scheduler {
            if (intervalSeconds == null) intervalSeconds = Duration.ofSeconds(10);
        }

        public long getIntervalSeconds() {
            return intervalSeconds.toSeconds();
        }

        public long getFixedRateMs() {
            return intervalSeconds.toMillis();
        }
    }

    public record Payment(
            boolean simulateEnabled
    ) {
        public boolean isSimulateEnabled() {
            return simulateEnabled;
        }

        public Mock getMock() {
            return new Mock();
        }

        public static class Mock {
            public boolean isAllowSimulateResult() { return true; }
            public void setAllowSimulateResult(boolean allow) {}
            public void setDefaultOutcome(Object outcome) {}
            public void setProcessingDelaySeconds(int seconds) {}
        }
    }

    public record Security(
            Login login
    ) {
        public Security {
            if (login == null) login = new Login(5, Duration.ofMinutes(15), Duration.ofMinutes(15));
        }

        public record Login(
                int maxAttempts,
                @DurationUnit(ChronoUnit.MINUTES) Duration windowMinutes,
                @DurationUnit(ChronoUnit.MINUTES) Duration blockMinutes
        ) {
            public Login {
                if (maxAttempts <= 0) maxAttempts = 5;
                if (windowMinutes == null) windowMinutes = Duration.ofMinutes(15);
                if (blockMinutes == null) blockMinutes = Duration.ofMinutes(15);
            }
        }
    }

    public record Cors(
            String allowedOrigins
    ) {
        public Cors {
            if (allowedOrigins == null) allowedOrigins = "http://localhost:3000";
        }
    }

    public Order getOrder() { return order; }
    public Auction getAuction() { return auction; }
    public Scheduler getScheduler() { return scheduler; }
    public Payment getPayment() { return payment; }
    public Security getSecurity() { return security; }
    public Cors getCors() { return cors; }
}
