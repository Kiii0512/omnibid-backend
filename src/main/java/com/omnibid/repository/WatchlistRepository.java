package com.omnibid.repository;

import com.omnibid.entity.Watchlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WatchlistRepository extends JpaRepository<Watchlist, Long> {
    List<Watchlist> findByUserId(Long userId);
    Optional<Watchlist> findByUserIdAndAuctionId(Long userId, Long auctionId);
    boolean existsByUserIdAndAuctionId(Long userId, Long auctionId);
    void deleteByUserIdAndAuctionId(Long userId, Long auctionId);
}
