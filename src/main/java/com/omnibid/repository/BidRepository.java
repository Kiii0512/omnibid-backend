package com.omnibid.repository;

import com.omnibid.entity.Bid;
import com.omnibid.enums.BidStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface BidRepository extends JpaRepository<Bid, Long> {

    List<Bid> findByAuctionIdOrderByAmountDesc(Long auctionId);

    Optional<Bid> findTopByAuctionIdAndStatusOrderByAmountDesc(Long auctionId, BidStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Bid b WHERE b.auctionId = :auctionId AND b.status = :status")
    List<Bid> findByAuctionIdAndStatusForUpdate(@Param("auctionId") Long auctionId, @Param("status") BidStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Bid b WHERE b.bidderId = :bidderId AND b.status IN :statuses")
    List<Bid> findByBidderIdAndStatusInForUpdate(@Param("bidderId") Long bidderId,
                                                @Param("statuses") Collection<BidStatus> statuses);

    List<Bid> findByBidderId(Long bidderId);

    List<Bid> findByAuctionId(Long auctionId);

    List<Bid> findByAuctionIdAndBidderId(Long auctionId, Long bidderId);

    @Query("SELECT DISTINCT b.auctionId FROM Bid b WHERE b.bidderId = :bidderId AND b.status IN :statuses")
    List<Long> findDistinctAuctionIdsByBidderIdAndStatusIn(@Param("bidderId") Long bidderId,
                                                          @Param("statuses") Collection<BidStatus> statuses);

    @Query("""
        SELECT b FROM Bid b, User u
        WHERE u.id = b.bidderId
          AND b.auctionId = :auctionId
          AND b.status IN :statuses
          AND u.status = com.omnibid.enums.UserStatus.ACTIVE
        ORDER BY b.amount DESC""")
    List<Bid> findValidBidsDesc(@Param("auctionId") Long auctionId,
                                @Param("statuses") Collection<BidStatus> statuses,
                                Pageable page);

    long countByCreatedAtBetween(LocalDateTime from, LocalDateTime to);
}
