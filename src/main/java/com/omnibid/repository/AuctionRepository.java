package com.omnibid.repository;

import com.omnibid.entity.Auction;
import com.omnibid.enums.AuctionStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface AuctionRepository extends JpaRepository<Auction, Long>, JpaSpecificationExecutor<Auction> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("SELECT a FROM Auction a WHERE a.id = :id")
    Optional<Auction> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("SELECT a FROM Auction a WHERE a.id IN :ids ORDER BY a.id ASC")
    List<Auction> findAllByIdInForUpdateOrderByIdAsc(@Param("ids") List<Long> ids);

    List<Auction> findByStatus(AuctionStatus status);

    Page<Auction> findByStatus(AuctionStatus status, Pageable pageable);

    List<Auction> findByStatusAndStartTimeLessThanEqual(AuctionStatus status, Instant now);

    List<Auction> findByStatusAndEndTimeLessThanEqual(AuctionStatus status, Instant now);

    @Query("SELECT a.id FROM Auction a WHERE a.status = :status AND a.startTime <= :now")
    List<Long> findIdsByStatusAndStartTimeLessThanEqual(@Param("status") AuctionStatus status, @Param("now") Instant now);

    @Query("SELECT a.id FROM Auction a WHERE a.status = :status AND a.endTime <= :now")
    List<Long> findIdsByStatusAndEndTimeLessThanEqual(@Param("status") AuctionStatus status, @Param("now") Instant now);

    Optional<Auction> findFirstByProductIdAndStatusIn(Long productId, Collection<AuctionStatus> statuses);

    List<Auction> findByProductIdAndStatusIn(Long productId, Collection<AuctionStatus> statuses);

    List<Auction> findByProductIdInAndStatusIn(Collection<Long> productIds, Collection<AuctionStatus> statuses);

    @Query("SELECT DISTINCT a.productId FROM Auction a WHERE a.productId IN :productIds")
    Set<Long> findProductIdsWithAnyAuction(@Param("productIds") Collection<Long> productIds);

    List<Auction> findBySellerIdAndStatusIn(Long sellerId, Collection<AuctionStatus> statuses);

    long countByStatus(AuctionStatus status);

    boolean existsByProductId(Long productId);

    Page<Auction> findBySellerId(Long sellerId, Pageable pageable);

    Page<Auction> findBySellerIdAndStatus(Long sellerId, AuctionStatus status, Pageable pageable);

    @Query("SELECT a.id FROM Auction a WHERE a.sellerId = :sellerId AND a.status IN :statuses ORDER BY a.id ASC")
    List<Long> findIdsBySellerIdAndStatusInOrderByIdAsc(@Param("sellerId") Long sellerId,
                                                        @Param("statuses") Collection<AuctionStatus> statuses);

    @Query("SELECT a.id FROM Auction a WHERE a.productId = :productId AND a.status IN :statuses ORDER BY a.id ASC")
    List<Long> findIdsByProductIdAndStatusInOrderByIdAsc(@Param("productId") Long productId,
                                                         @Param("statuses") Collection<AuctionStatus> statuses);

    @Query("""
        SELECT a FROM Auction a
        WHERE (:status IS NULL OR a.status = :status)
          AND (:sellerId IS NULL OR a.sellerId = :sellerId)
          AND (:keyword IS NULL OR EXISTS (
              SELECT 1 FROM Product p
              WHERE p.id = a.productId AND LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
          ))
    """)
    Page<Auction> findAdminAuctions(@Param("keyword") String keyword,
                                    @Param("status") AuctionStatus status,
                                    @Param("sellerId") Long sellerId,
                                    Pageable pageable);

    @Query("SELECT a.status, COUNT(a) FROM Auction a GROUP BY a.status")
    List<Object[]> countGroupedByStatus();

    @Query("SELECT COUNT(a) FROM Auction a JOIN Product p ON a.productId = p.id WHERE p.categoryId = :categoryId AND a.status = com.omnibid.enums.AuctionStatus.ACTIVE")
    long countActiveAuctionsByCategoryId(@Param("categoryId") Long categoryId);

    @Query("SELECT p.categoryId, COUNT(a) FROM Auction a JOIN Product p ON a.productId = p.id WHERE a.status = com.omnibid.enums.AuctionStatus.ACTIVE GROUP BY p.categoryId")
    List<Object[]> countActiveAuctionsGroupedByCategory();
}
