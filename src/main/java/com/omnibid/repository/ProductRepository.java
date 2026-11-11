package com.omnibid.repository;

import com.omnibid.entity.Product;
import com.omnibid.enums.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findBySellerId(Long sellerId);

    List<Product> findByCategoryIdAndStatus(Long categoryId, ProductStatus status);

    Page<Product> findBySellerId(Long sellerId, Pageable pageable);

    Page<Product> findBySellerIdAndStatus(Long sellerId, ProductStatus status, Pageable pageable);

    Page<Product> findByStatus(ProductStatus status, Pageable pageable);

    @Query("SELECT p.status, COUNT(p) FROM Product p WHERE p.sellerId = :sellerId GROUP BY p.status")
    List<Object[]> countProductStatusBySellerId(@Param("sellerId") Long sellerId);
}
