package com.omnibid.repository;

import com.omnibid.entity.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    Optional<Category> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);

    @Query("""
        SELECT c FROM Category c
        WHERE (:keyword IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\'
               OR LOWER(c.slug) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\')
    """)
    Page<Category> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);
}
