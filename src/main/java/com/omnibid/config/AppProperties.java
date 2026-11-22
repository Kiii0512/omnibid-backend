package com.omnibid.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
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
    @ConstructorBinding
    public AppProperties(
            Order order,
            Auction auction,
            Scheduler scheduler,
            Payment payment,
            Security security,
            Cors cors
    ) {
        this.order = order != null ? order : new Order(Duration.ofHours(48), Duration.ofDays(7));
        this.auction = auction != null ? auction : new Auction(Duration.ofMinutes(60), Duration.ofDays(30));
        this.scheduler = scheduler != null ? scheduler : new Scheduler(Duration.ofSeconds(10));
        this.payment = payment != null ? payment : new Payment(false);
        this.security = security != null ? security : new Security(new Security.Login(5, Duration.ofMinutes(15), Duration.ofMinutes(15)));
        this.cors = cors != null ? cors : new Cors("http://localhost:3000");
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

        public Duration getPaymentDeadlineDuration() {
            return paymentDeadlineHours;
        }

        public Duration getConfirmDeadlineDuration() {
            return confirmDeadlineDays;
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

        public Duration getMinDuration() {
            return minDurationMinutes;
        }

        public Duration getMaxDuration() {
            return maxDurationDays;
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
