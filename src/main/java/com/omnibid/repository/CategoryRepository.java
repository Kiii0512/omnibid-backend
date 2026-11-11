package com.omnibid.repository;

import com.omnibid.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    Optional<Category> findBySlug(String slug);
    boolean existsBySlug(String slug);

    // Đếm số phiên ACTIVE của 1 danh mục cụ thể
    @Query("""
        SELECT COUNT(a) FROM Auction a 
        JOIN Product p ON a.productId = p.id 
        WHERE p.categoryId = :categoryId AND a.status = com.omnibid.enums.AuctionStatus.ACTIVE
    """)
    long countActiveAuctionsByCategoryId(@Param("categoryId") Long categoryId);

    // Lấy thống kê số phiên ACTIVE theo từng categoryId
    @Query("""
        SELECT p.categoryId, COUNT(a) 
        FROM Auction a 
        JOIN Product p ON a.productId = p.id 
        WHERE a.status = com.omnibid.enums.AuctionStatus.ACTIVE 
        GROUP BY p.categoryId
    """)
    List<Object[]> countActiveAuctionsGroupedByCategory();
}