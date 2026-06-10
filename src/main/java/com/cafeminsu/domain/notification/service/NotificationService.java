package com.cafeminsu.domain.notification.service;

import com.cafeminsu.domain.notification.dto.NotificationListItemRes;
import com.cafeminsu.domain.notification.dto.UnreadCountRes;
import com.cafeminsu.domain.notification.entity.Notification;
import com.cafeminsu.domain.notification.entity.NotificationType;
import com.cafeminsu.domain.notification.repository.NotificationRepository;
import com.cafeminsu.domain.order.entity.Order;
import com.cafeminsu.domain.user.entity.User;
import com.cafeminsu.domain.user.repository.UserRepository;
import com.cafeminsu.global.common.BaseResponseStatus;
import com.cafeminsu.global.exception.BaseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final FcmClient fcmClient;

    /* =========================================================
     * 외부 호출용 — Order 등 다른 도메인에서 알림 발송
     * ========================================================= */

    /** Order 수락 알림 — OrderService.acceptOrder에서 호출 */
    @Transactional
    public void sendOrderAccepted(Order order) {
        if (order.getUserId() == null) return;   // 키오스크 비회원 주문
        send(order.getUserId(), NotificationType.ORDER,
                "주문이 수락되었어요",
                "주문번호 " + order.getOrderNumber() + " - 곧 준비됩니다.",
                order.getId());
    }

    /** Order 준비 완료 알림 */
    @Transactional
    public void sendOrderReady(Order order) {
        if (order.getUserId() == null) return;
        send(order.getUserId(), NotificationType.ORDER,
                "픽업해주세요",
                "주문번호 " + order.getOrderNumber() + "이 준비됐어요.",
                order.getId());
    }

    /** 기프티콘 받음 알림 — GifticonService.purchase에서 호출 가능 */
    @Transactional
    public void sendGifticonReceived(Long receiverId, String senderNickname, Integer amount, Long gifticonId) {
        send(receiverId, NotificationType.GIFTICON,
                "기프티콘이 도착했어요",
                senderNickname + "님이 " + amount + "원 기프티콘을 보냈습니다.",
                gifticonId);
    }

    /** 공통 발송 — DB 저장 + FCM 발송 */
    private void send(Long userId, NotificationType type, String title, String body, Long relatedEntityId) {
        notificationRepository.save(Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .body(body)
                .relatedEntityId(relatedEntityId)
                .build());

        String fcmToken = userRepository.findById(userId)
                .map(User::getFcmToken).orElse(null);
        try {
            fcmClient.send(fcmToken, title, body);
        } catch (Exception e) {
            // FCM 실패는 알림 저장에 영향 주지 않음
            log.warn("[Notification] FCM send failed userId={}: {}", userId, e.getMessage());
        }
    }

    /* =========================================================
     * 사용자 조회용 API
     * ========================================================= */

    /** 알림 목록 — isRead 필터 + 페이지 */
    public List<NotificationListItemRes> getList(Long userId, Boolean isRead, int limit) {
        var pageable = PageRequest.of(0, Math.min(limit, 100));
        List<Notification> notifications = (isRead != null && !isRead)
                ? notificationRepository.findByUserIdAndIsReadFalseOrderByIdDesc(userId, pageable)
                : notificationRepository.findByUserIdOrderByIdDesc(userId, pageable);
        return notifications.stream().map(NotificationListItemRes::from).toList();
    }

    /** 단건 읽음 처리 */
    @Transactional
    public void markRead(Long userId, Long notificationId) {
        Notification n = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.NOTIFICATION_NOT_FOUND));
        if (!n.isOwnedBy(userId)) {
            throw new BaseException(BaseResponseStatus.ACCESS_DENIED);
        }
        n.markRead();
    }

    /** 전체 읽음 처리 */
    @Transactional
    public void markAllRead(Long userId) {
        int updated = notificationRepository.markAllReadByUserId(userId);
        log.info("[Notification] markAllRead userId={} updated={}", userId, updated);
    }

    /** 안 읽은 알림 개수 (헤더 뱃지) */
    public UnreadCountRes getUnreadCount(Long userId) {
        return new UnreadCountRes(notificationRepository.countByUserIdAndIsReadFalse(userId));
    }
}
