package com.cafeminsu.domain.notification.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Firebase Cloud Messaging 발송 클라이언트.
 *
 * {@link com.cafeminsu.global.config.FirebaseConfig}가 서비스 계정 키로 FirebaseMessaging 빈을
 * 등록하면 실제 푸시를 발송한다. 키가 없어 빈이 없는 환경에서는 mock(로그)으로 동작한다.
 *
 * fcmToken이 null인 사용자(아직 앱에서 토큰 등록 안 함)는 silent skip.
 * 발송 실패는 로그만 남기고 예외를 전파하지 않는다 — 알림 DB 저장에 영향을 주지 않기 위함.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FcmClient {

    private final ObjectProvider<FirebaseMessaging> firebaseMessagingProvider;

    public void send(String fcmToken, String title, String body) {
        if (fcmToken == null || fcmToken.isBlank()) {
            log.debug("[FCM] skip — token not registered");
            return;
        }

        FirebaseMessaging messaging = firebaseMessagingProvider.getIfAvailable();
        if (messaging == null) {
            log.info("[FCM:mock] send token={} title='{}' body='{}'",
                    truncate(fcmToken, 16), title, body);
            return;
        }

        Message message = Message.builder()
                .setToken(fcmToken)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .build();
        try {
            String messageId = messaging.send(message);
            log.info("[FCM] sent token={} messageId={}", truncate(fcmToken, 16), messageId);
        } catch (FirebaseMessagingException e) {
            log.warn("[FCM] send failed token={} code={}: {}",
                    truncate(fcmToken, 16), e.getMessagingErrorCode(), e.getMessage());
        }
    }

    private String truncate(String s, int n) {
        return s.length() > n ? s.substring(0, n) + "..." : s;
    }
}
