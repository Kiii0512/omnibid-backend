package com.omnibid.repository;

import com.omnibid.entity.Auction;
import com.omnibid.enums.AuctionStatus;
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
public interface AuctionRepository extends JpaRepository<Auction, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Auction a WHERE a.id = :id")
    Optional<Auction> findByIdForUpdate(@Param("id") Long id);

    List<Auction> findByStatus(AuctionStatus status);

    Page<Auction> findByStatus(AuctionStatus status, Pageable pageable);

    List<Auction> findByStatusAndStartTimeLessThanEqual(AuctionStatus status, LocalDateTime now);

    List<Auction> findByStatusAndEndTimeLessThanEqual(AuctionStatus status, LocalDateTime now);
}