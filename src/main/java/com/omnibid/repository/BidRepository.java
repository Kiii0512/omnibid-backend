package com.omnibid.repository;

import com.omnibid.entity.Bid;
import com.omnibid.enums.BidStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BidRepository extends JpaRepository<Bid, Long> {
    List<Bid> findByAuctionIdOrderByAmountDesc(Long auctionId);
    Optional<Bid> findTopByAuctionIdAndStatusOrderByAmountDesc(Long auctionId, BidStatus status);
    List<Bid> findByBidderId(Long bidderId);
}
