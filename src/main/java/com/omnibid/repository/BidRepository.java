package com.omnibid.repository;

import com.omnibid.entity.Bid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface BidRepository extends JpaRepository<Bid, Long> {

    List<Bid> findByAuctionIdOrderByAmountDesc(Long auctionId);

    Optional<Bid> findTopByAuctionIdOrderByAmountDesc(Long auctionId);

    List<Bid> findByBidderId(Long bidderId);

    Page<Bid> findByBidderIdOrderByCreatedAtDesc(Long bidderId, Pageable pageable);

    Page<Bid> findByAuctionId(Long auctionId, Pageable pageable);

    Page<Bid> findByBidderIdAndAuctionId(Long bidderId, Long auctionId, Pageable pageable);

    long countByBidderId(Long bidderId);

    long countByAuctionId(Long auctionId);

    boolean existsByAuctionId(Long auctionId);

    List<Bid> findByAuctionId(Long auctionId);

    List<Bid> findByAuctionIdAndBidderId(Long auctionId, Long bidderId);

    long countByCreatedAtBetween(Instant from, Instant to);

    @org.springframework.data.jpa.repository.Query("""
        SELECT b FROM Bid b
        WHERE b.bidderId = :bidderId
          AND (:auctionId IS NULL OR b.auctionId = :auctionId)
        ORDER BY b.createdAt DESC
    """)
    Page<Bid> findMyBids(@org.springframework.data.repository.query.Param("bidderId") Long bidderId,
                         @org.springframework.data.repository.query.Param("auctionId") Long auctionId,
                         Pageable pageable);
}
