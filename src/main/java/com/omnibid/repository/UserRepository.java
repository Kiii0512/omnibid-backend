package com.omnibid.repository;

import com.omnibid.entity.User;
import com.omnibid.enums.UserRole;
import com.omnibid.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE User u SET u.status = :status WHERE u.id = :id")
    int updateStatus(@Param("id") Long id, @Param("status") UserStatus status);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE User u SET u.tokenVersion = u.tokenVersion + 1 WHERE u.id = :id")
    int incrementTokenVersion(@Param("id") Long id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE User u SET u.passwordHash = :passwordHash, u.tokenVersion = u.tokenVersion + 1 WHERE u.id = :id")
    int updatePasswordAndIncrementTokenVersion(@Param("id") Long id, @Param("passwordHash") String passwordHash);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE User u SET u.fullName = :fullName, u.phoneNumber = :phoneNumber, u.address = :address WHERE u.id = :id")
    int updateProfileFields(@Param("id") Long id,
                            @Param("fullName") String fullName,
                            @Param("phoneNumber") String phoneNumber,
                            @Param("address") String address);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE User u SET u.avatarUrl = :avatarUrl, u.avatarPublicId = :avatarPublicId WHERE u.id = :id")
    int updateAvatarFields(@Param("id") Long id,
                           @Param("avatarUrl") String avatarUrl,
                           @Param("avatarPublicId") String avatarPublicId);

    @Query("""
        SELECT u FROM User u
        WHERE (:status IS NULL OR u.status = :status)
          AND (:role IS NULL OR u.role = :role)
          AND (:keyword IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')))
    """)
    Page<User> findAdminUsers(@Param("keyword") String keyword,
                              @Param("status") UserStatus status,
                              @Param("role") UserRole role,
                              Pageable pageable);

    long countByStatus(UserStatus status);

    long countByCreatedAtBetween(Instant from, Instant to);
}
