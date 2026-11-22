package com.omnibid.repository;

import com.omnibid.entity.Bid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface BidRepository extends JpaRepository<Bid, Long> {

    boolean existsByAuctionId(Long auctionId);

    Page<Bid> findByAuctionId(Long auctionId, Pageable pageable);

    Page<Bid> findByBidderId(Long bidderId, Pageable pageable);

    Page<Bid> findByBidderIdAndAuctionId(Long bidderId, Long auctionId, Pageable pageable);

    long countByAuctionId(Long auctionId);

    List<Bid> findByAuctionIdOrderByAmountDesc(Long auctionId);

    List<Bid> findByBidderId(Long bidderId);

    Page<Bid> findByBidderIdOrderByCreatedAtDesc(Long bidderId, Pageable pageable);

    long countByBidderId(Long bidderId);

    @Query("SELECT COUNT(DISTINCT b.auctionId) FROM Bid b WHERE b.bidderId = :bidderId")
    long countDistinctAuctionsByBidderId(@Param("bidderId") Long bidderId);

    List<Bid> findByAuctionId(Long auctionId);

    List<Bid> findByAuctionIdAndBidderId(Long auctionId, Long bidderId);

    long countByCreatedAtBetween(Instant from, Instant to);
}
