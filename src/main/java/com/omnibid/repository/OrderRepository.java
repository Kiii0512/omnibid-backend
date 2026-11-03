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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    Optional<Order> findByAuctionId(Long auctionId);

    List<Order> findByBuyerId(Long buyerId);

    List<Order> findByProductId(Long productId);

    List<Order> findByProductIdAndStatusIn(Long productId, Collection<OrderStatus> statuses);

    @Query("SELECT o FROM Order o WHERE o.status = com.omnibid.enums.OrderStatus.PENDING_PAYMENT AND (o.buyerId = :userId OR o.sellerId = :userId) ORDER BY o.id ASC")
    List<Order> findUnpaidOrdersByUser(@Param("userId") Long userId);

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

    long countByStatus(OrderStatus status);

    @Query("SELECT COALESCE(SUM(o.finalPrice), 0) FROM Order o WHERE o.status = com.omnibid.enums.OrderStatus.COMPLETED AND o.completedAt >= :from AND o.completedAt <= :to")
    BigDecimal sumCompletedRevenue(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
        SELECT o FROM Order o
        WHERE (:status IS NULL OR o.status = :status)
          AND (:buyerId IS NULL OR o.buyerId = :buyerId)
          AND (:sellerId IS NULL OR o.sellerId = :sellerId)
          AND (:from IS NULL OR o.createdAt >= :from)
          AND (:to IS NULL OR o.createdAt <= :to)
    """)
    Page<Order> findAdminOrders(@Param("status") OrderStatus status,
                                @Param("buyerId") Long buyerId,
                                @Param("sellerId") Long sellerId,
                                @Param("from") LocalDateTime from,
                                @Param("to") LocalDateTime to,
                                Pageable pageable);
}
