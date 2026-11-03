package com.omnibid.repository;

import com.omnibid.entity.Order;
import com.omnibid.enums.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    Optional<Order> findByAuctionId(Long auctionId);

    List<Order> findByBuyerId(Long buyerId);

    @Query("SELECT o FROM Order o WHERE (o.buyerId = :userId OR o.sellerId = :userId) AND (:status IS NULL OR o.status = :status)")
    Page<Order> findByBuyerOrSeller(@Param("userId") Long userId, @Param("status") OrderStatus status, Pageable pageable);

    @Query("SELECT o FROM Order o WHERE o.buyerId = :buyerId AND (:status IS NULL OR o.status = :status)")
    Page<Order> findByBuyerId(@Param("buyerId") Long buyerId, @Param("status") OrderStatus status, Pageable pageable);

    @Query("SELECT o FROM Order o WHERE o.sellerId = :sellerId AND (:status IS NULL OR o.status = :status)")
    Page<Order> findBySellerId(@Param("sellerId") Long sellerId, @Param("status") OrderStatus status, Pageable pageable);

    @Query("SELECT o FROM Order o WHERE (:status IS NULL OR o.status = :status)")
    Page<Order> findAllByStatus(@Param("status") OrderStatus status, Pageable pageable);

    List<Order> findByStatusAndPaymentDeadlineLessThanEqual(OrderStatus status, LocalDateTime now);

    @Query("SELECT o FROM Order o WHERE o.status = 'PENDING_PAYMENT' AND o.reminder24hSentAt IS NULL AND o.paymentDeadline <= :threshold24h AND o.paymentDeadline > :now")
    List<Order> findOrdersFor24hReminder(@Param("threshold24h") LocalDateTime threshold24h, @Param("now") LocalDateTime now);

    @Query("SELECT o FROM Order o WHERE o.status = 'PENDING_PAYMENT' AND o.reminder2hSentAt IS NULL AND o.paymentDeadline <= :threshold2h AND o.paymentDeadline > :now")
    List<Order> findOrdersFor2hReminder(@Param("threshold2h") LocalDateTime threshold2h, @Param("now") LocalDateTime now);

    List<Order> findByStatusAndConfirmDeadlineLessThanEqual(OrderStatus status, LocalDateTime now);
}
