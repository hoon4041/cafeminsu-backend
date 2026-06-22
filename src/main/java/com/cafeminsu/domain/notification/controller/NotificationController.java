package com.cafeminsu.domain.notification.controller;

import com.cafeminsu.domain.notification.dto.NotificationListItemRes;
import com.cafeminsu.domain.notification.dto.UnreadCountRes;
import com.cafeminsu.domain.notification.service.NotificationService;
import com.cafeminsu.global.security.LoginUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "8. Notification", description = "알림 API")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /* 1. 알림 목록 — isRead 필터 + limit */
    @Operation(summary = "알림 목록",
            description = "isRead=false 전달 시 안 읽은 것만. limit 기본 20, 최대 100.")
    @GetMapping
    public List<NotificationListItemRes> list(
            @LoginUserId Long userId,
            @RequestParam(required = false) Boolean isRead,
            @RequestParam(defaultValue = "20") int limit) {
        return notificationService.getList(userId, isRead, limit);
    }

    /* 2. 단건 읽음 처리 */
    @Operation(summary = "알림 읽음 처리")
    @PatchMapping("/{id}/read")
    public void markRead(@LoginUserId Long userId,
                         @PathVariable Long id) {
        notificationService.markRead(userId, id);
    }

    /* 3. 전체 읽음 처리 */
    @Operation(summary = "전체 읽음 처리")
    @PatchMapping("/read-all")
    public void markAllRead(@LoginUserId Long userId) {
        notificationService.markAllRead(userId);
    }

    /* 4. 안 읽은 개수 (헤더 뱃지) */
    @Operation(summary = "안 읽은 알림 개수")
    @GetMapping("/unread-count")
    public UnreadCountRes unreadCount(@LoginUserId Long userId) {
        return notificationService.getUnreadCount(userId);
    }
}
