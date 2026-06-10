package com.cafeminsu.domain.notification.dto;

import com.cafeminsu.domain.notification.entity.Notification;
import com.cafeminsu.domain.notification.entity.NotificationType;

import java.time.LocalDateTime;

public record NotificationListItemRes(
        Long id,
        String title,
        String body,
        NotificationType type,
        boolean isRead,
        Long relatedEntityId,
        LocalDateTime createdAt
) {
    public static NotificationListItemRes from(Notification n) {
        return new NotificationListItemRes(
                n.getId(), n.getTitle(), n.getBody(),
                n.getType(), n.isRead(), n.getRelatedEntityId(),
                n.getCreatedAt()
        );
    }
}
