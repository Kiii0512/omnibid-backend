package com.omnibid.repository;

import com.omnibid.entity.Watchlist;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WatchlistRepository extends JpaRepository<Watchlist, Long> {

    List<Watchlist> findByUserId(Long userId);

    Optional<Watchlist> findByUserIdAndAuctionId(Long userId, Long auctionId);

    boolean existsByUserIdAndAuctionId(Long userId, Long auctionId);

    void deleteByUserIdAndAuctionId(Long userId, Long auctionId);

    Page<Watchlist> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    long countByUserId(Long userId);

    @Query("""
        SELECT a.status, COUNT(w)
        FROM Watchlist w
        JOIN Auction a ON w.auctionId = a.id
        WHERE w.userId = :userId
        GROUP BY a.status
    """)
    List<Object[]> countByUserIdGroupByAuctionStatus(@Param("userId") Long userId);
}
