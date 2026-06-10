package com.cafeminsu.domain.notification.repository;

import com.cafeminsu.domain.notification.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);

    List<Notification> findByUserIdAndIsReadFalseOrderByIdDesc(Long userId, Pageable pageable);

    long countByUserIdAndIsReadFalse(Long userId);

    /** 전체 읽음 처리 — bulk UPDATE */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.userId = :userId AND n.isRead = false")
    int markAllReadByUserId(@Param("userId") Long userId);
}
