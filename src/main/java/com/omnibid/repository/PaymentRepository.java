package com.omnibid.repository;

import com.omnibid.entity.Payment;
import com.omnibid.enums.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    Optional<Payment> findByOrderIdAndIdempotencyKey(Long orderId, String idempotencyKey);

    Optional<Payment> findByTransactionRef(String transactionRef);

    List<Payment> findByOrderId(Long orderId);

    List<Payment> findByOrderIdOrderByAttemptNoDesc(Long orderId);

    List<Payment> findTop5ByOrderIdOrderByCreatedAtDesc(Long orderId);

    Optional<Payment> findTopByOrderIdOrderByAttemptNoDesc(Long orderId);

    boolean existsByOrderIdAndStatus(Long orderId, PaymentStatus status);

    List<Payment> findByStatusAndCreatedAtLessThanEqual(PaymentStatus status, LocalDateTime dateTime);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.transactionRef = :transactionRef")
    Optional<Payment> findByTransactionRefForUpdate(@Param("transactionRef") String transactionRef);
}
