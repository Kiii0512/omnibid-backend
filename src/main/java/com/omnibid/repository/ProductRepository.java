package com.omnibid.repository;

import com.omnibid.entity.Product;
import com.omnibid.enums.ProductStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("SELECT p FROM Product p WHERE p.id IN :ids ORDER BY p.id ASC")
    List<Product> findAllByIdInForUpdateOrderByIdAsc(@Param("ids") List<Long> ids);

    List<Product> findBySellerId(Long sellerId);

    Page<Product> findBySellerId(Long sellerId, Pageable pageable);

    Page<Product> findBySellerIdAndStatus(Long sellerId, ProductStatus status, Pageable pageable);

    Page<Product> findByStatus(ProductStatus status, Pageable pageable);

    List<Product> findBySellerIdAndStatusIn(Long sellerId, Collection<ProductStatus> statuses);

    List<Product> findByCategoryIdAndStatus(Long categoryId, ProductStatus status);

    boolean existsByCategoryId(Long categoryId);

    long countByStatus(ProductStatus status);

    long countBySellerId(Long sellerId);

    long countBySellerIdAndStatus(Long sellerId, ProductStatus status);

    @Query("""
        SELECT p FROM Product p
        WHERE (:status IS NULL OR p.status = :status)
          AND (:sellerId IS NULL OR p.sellerId = :sellerId)
          AND (:categoryId IS NULL OR p.categoryId = :categoryId)
    """)
    Page<Product> findAdminProducts(@Param("status") ProductStatus status,
                                    @Param("sellerId") Long sellerId,
                                    @Param("categoryId") Long categoryId,
                                    Pageable pageable);
}
