package com.omnibid.repository;

import com.omnibid.entity.Order;
import com.omnibid.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByAuctionId(Long auctionId);

    List<Order> findByBuyerId(Long buyerId);

    List<Order> findByStatusAndPaymentDeadlineLessThanEqual(OrderStatus status, LocalDateTime now);

    List<Order> findByStatusAndConfirmDeadlineLessThanEqual(OrderStatus status, LocalDateTime now);
}