package com.omnibid.repository;

import com.omnibid.entity.Payment;
import com.omnibid.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderIdAndIdempotencyKey(Long orderId, String idempotencyKey);

    @Query("SELECT COALESCE(MAX(p.attemptNo), 0) FROM Payment p WHERE p.orderId = :orderId")
    int findMaxAttemptNo(@Param("orderId") Long orderId);

    Page<Payment> findByOrderId(Long orderId, Pageable pageable);

    List<Payment> findByOrderId(Long orderId);

    List<Payment> findByOrderIdOrderByAttemptNoDesc(Long orderId);

    boolean existsByOrderIdAndStatus(Long orderId, PaymentStatus status);

    long countByStatus(PaymentStatus status);
}
