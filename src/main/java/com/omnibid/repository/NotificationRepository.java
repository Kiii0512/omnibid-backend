package com.omnibid.repository;

import com.omnibid.entity.Notification;
import com.omnibid.enums.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("""
        SELECT n FROM Notification n
        WHERE n.userId = :userId
          AND (:isRead IS NULL OR n.isRead = :isRead)
          AND (:type IS NULL OR n.type = :type)
    """)
    Page<Notification> findNotifications(@Param("userId") Long userId,
                                         @Param("isRead") Boolean isRead,
                                         @Param("type") NotificationType type,
                                         Pageable pageable);

    long countByUserIdAndIsReadFalse(Long userId);
}
