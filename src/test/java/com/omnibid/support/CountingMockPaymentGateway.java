package com.omnibid.support;

import com.omnibid.config.AppProperties;
import com.omnibid.gateway.DefaultMockPaymentGateway;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class CountingMockPaymentGateway extends DefaultMockPaymentGateway {

    private final AtomicInteger chargeCount = new AtomicInteger(0);
    private volatile long delayMs = 0;
    private volatile CountDownLatch latch = null;

    public CountingMockPaymentGateway(AppProperties appProperties) {
        super(appProperties);
    }

    @Override
    public GatewayResult charge(GatewayRequest request) {
        chargeCount.incrementAndGet();

        if (delayMs > 0) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (latch != null) {
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        return super.charge(request);
    }

    public int chargeCount() {
        return chargeCount.get();
    }

    public void reset() {
        chargeCount.set(0);
        delayMs = 0;
        latch = null;
    }

    public void setDelayMs(long delayMs) {
        this.delayMs = delayMs;
    }

    public void setLatch(CountDownLatch latch) {
        this.latch = latch;
    }

    public void setDisableDelayedFinalization(boolean disable) {
    }
}
