package com.omnibid.repository;

import com.omnibid.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {
    List<ProductImage> findByProductId(Long productId);
    List<ProductImage> findByProductIdOrderByIdAsc(Long productId);
    Optional<ProductImage> findByProductIdAndIsPrimaryTrue(Long productId);
    void deleteByProductId(Long productId);
    long countByProductId(Long productId);
}
