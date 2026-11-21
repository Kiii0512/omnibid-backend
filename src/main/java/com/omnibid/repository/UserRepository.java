package com.omnibid.repository;

import com.omnibid.entity.User;
import com.omnibid.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("""
        SELECT u FROM User u
        WHERE (:status IS NULL OR u.status = :status)
          AND (:role IS NULL OR u.role = :role)
          AND (:q IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :q, '%')))
    """)
    Page<User> findAdminUsers(@Param("status") UserStatus status, @Param("role") com.omnibid.enums.UserRole role, @Param("q") String q, Pageable pageable);

    long countByStatus(UserStatus status);

    long countByCreatedAtBetween(Instant from, Instant to);
}
