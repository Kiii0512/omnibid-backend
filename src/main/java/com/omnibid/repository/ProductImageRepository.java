package com.omnibid.repository;

import com.omnibid.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    List<ProductImage> findByProductId(Long productId);

    List<ProductImage> findByProductIdOrderByIsPrimaryDescCreatedAtAsc(Long productId);

    List<ProductImage> findByProductIdInOrderByIsPrimaryDescCreatedAtAsc(Collection<Long> productIds);

    long countByProductId(Long productId);

    Optional<ProductImage> findByIdAndProductId(Long id, Long productId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ProductImage pi SET pi.isPrimary = false WHERE pi.productId = :productId")
    void resetPrimaryByProductId(@Param("productId") Long productId);

    void deleteByProductId(Long productId);

    void deleteAllByProductId(Long productId);
}
