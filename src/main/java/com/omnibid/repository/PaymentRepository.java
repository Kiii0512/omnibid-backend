package com.omnibid.repository;

import com.omnibid.entity.Payment;
import com.omnibid.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
    List<Payment> findByOrderId(Long orderId);
    Optional<Payment> findTopByOrderIdOrderByAttemptNoDesc(Long orderId);
    boolean existsByOrderIdAndStatus(Long orderId, PaymentStatus status);
}
