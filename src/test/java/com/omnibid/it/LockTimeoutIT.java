package com.omnibid.it;

import com.omnibid.entity.Auction;
import com.omnibid.entity.Product;
import com.omnibid.enums.AuctionStatus;
import com.omnibid.enums.ProductStatus;
import com.omnibid.repository.AuctionRepository;
import com.omnibid.support.BaseIT;
import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PessimisticLockException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class LockTimeoutIT extends BaseIT {

    @Autowired
    private AuctionRepository auctionRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("INT-LOCK-01 HIGH-03: MySQL session innodb_lock_wait_timeout=5s và TX2 bị ngắt trong ~5s thay vì 50s")
    void testMysqlSessionLockWaitTimeoutIsFiveSeconds() throws Exception {
        Integer sessionTimeout = jdbcTemplate.queryForObject("SELECT @@SESSION.innodb_lock_wait_timeout", Integer.class);
        assertThat(sessionTimeout).isEqualTo(5);

        Instant now = mutableClock.instant();
        Product product = seed.createProduct(seller, category, "Lock Test Watch", ProductStatus.IN_AUCTION);
        Auction auction = seed.createAuction(seller, product, new BigDecimal("500000"), new BigDecimal("10000"),
                now.minus(10, ChronoUnit.MINUTES), now.plus(20, ChronoUnit.MINUTES), AuctionStatus.ACTIVE);

        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> holder = executor.submit(() -> txTemplate.executeWithoutResult(status -> {
                auctionRepository.findByIdForUpdate(auction.getId());
                lockAcquired.countDown();
                try {
                    releaseLock.await(15, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));

            assertThat(lockAcquired.await(5, TimeUnit.SECONDS)).isTrue();

            long startNanos = System.nanoTime();
            Throwable thrown = null;
            try {
                txTemplate.executeWithoutResult(status -> auctionRepository.findByIdForUpdate(auction.getId()));
            } catch (Throwable ex) {
                thrown = ex;
            }
            Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);

            releaseLock.countDown();
            holder.get(5, TimeUnit.SECONDS);

            assertThat(thrown)
                    .isNotNull()
                    .isInstanceOfAny(
                            PessimisticLockingFailureException.class,
                            CannotAcquireLockException.class,
                            LockTimeoutException.class,
                            PessimisticLockException.class
                    );
            assertThat(elapsed.toMillis()).isGreaterThanOrEqualTo(4000L).isLessThan(9500L);

            // Pool vẫn hoạt động bình thường sau timeout
            assertThat(auctionRepository.findById(auction.getId())).isPresent();
        } finally {
            releaseLock.countDown();
            executor.shutdownNow();
        }
    }
}
