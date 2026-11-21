package com.omnibid.repository;

import com.omnibid.entity.Payment;
import com.omnibid.enums.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    Optional<Payment> findByOrderIdAndIdempotencyKey(Long orderId, String idempotencyKey);

    List<Payment> findByOrderId(Long orderId);

    Page<Payment> findByOrderId(Long orderId, Pageable pageable);

    List<Payment> findByOrderIdOrderByAttemptNoAsc(Long orderId);

    List<Payment> findByOrderIdOrderByAttemptNoDesc(Long orderId);

    List<Payment> findTop5ByOrderIdOrderByCreatedAtDesc(Long orderId);

    Optional<Payment> findTopByOrderIdOrderByAttemptNoDesc(Long orderId);

    @Query("SELECT MAX(p.attemptNo) FROM Payment p WHERE p.orderId = :orderId")
    Optional<Integer> findMaxAttemptNo(@Param("orderId") Long orderId);

    boolean existsByOrderIdAndStatus(Long orderId, PaymentStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);

    long countByStatus(PaymentStatus status);
}
