package com.omnibid.service.impl;

import com.omnibid.config.AppProperties;
import com.omnibid.entity.Auction;
import com.omnibid.entity.Order;
import com.omnibid.enums.OrderStatus;
import com.omnibid.repository.OrderRepository;
import com.omnibid.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final AppProperties appProperties;

    @Override
    @Transactional
    public Order createFromAuction(Auction auction) {
        return orderRepository.findByAuctionId(auction.getId())
                .orElseGet(() -> {
                    int deadlineHours = appProperties.getOrder().getPaymentDeadlineHours();
                    Order order = Order.builder()
                            .auctionId(auction.getId())
                            .productId(auction.getProductId())
                            .sellerId(auction.getSellerId())
                            .buyerId(auction.getWinnerId())
                            .finalPrice(auction.getFinalPrice())
                            .status(OrderStatus.PENDING_PAYMENT)
                            .paymentDeadline(auction.getClosedAt().plusHours(deadlineHours))
                            .build();
                    log.info("Creating order for auction {}: buyer={}, amount={}",
                            auction.getId(), auction.getWinnerId(), auction.getFinalPrice());
                    return orderRepository.save(order);
                });
    }
}
