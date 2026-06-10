package com.cafeminsu.domain.notification.entity;

import com.cafeminsu.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "notifications",
        indexes = {
                @Index(name = "idx_noti_user", columnList = "user_id"),
                @Index(name = "idx_noti_user_unread", columnList = "user_id, is_read")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 500)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationType type;

    @Column(name = "is_read", nullable = false)
    private boolean isRead;

    /** 관련 엔티티 ID — 예: ORDER이면 orderId. 알림 클릭 시 깊이 진입에 사용. */
    @Column(name = "related_entity_id")
    private Long relatedEntityId;

    @Builder
    private Notification(Long userId, String title, String body,
                         NotificationType type, Long relatedEntityId) {
        this.userId = userId;
        this.title = title;
        this.body = body;
        this.type = type;
        this.relatedEntityId = relatedEntityId;
        this.isRead = false;
    }

    public void markRead() {
        this.isRead = true;
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }
}
