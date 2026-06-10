package com.cafeminsu.domain.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Firebase Cloud Messaging 발송 클라이언트.
 *
 * 현재 MVP는 mock — 로그만 출력. 운영에선 Firebase Admin SDK로 교체:
 * <pre>
 *   Message message = Message.builder()
 *       .putData("type", type)
 *       .setNotification(Notification.builder().setTitle(title).setBody(body).build())
 *       .setToken(fcmToken)
 *       .build();
 *   FirebaseMessaging.getInstance().send(message);
 * </pre>
 *
 * fcmToken이 null인 사용자(아직 앱에서 토큰 등록 안 함)는 silent skip.
 */
@Slf4j
@Component
public class FcmClient {

    public void send(String fcmToken, String title, String body) {
        if (fcmToken == null || fcmToken.isBlank()) {
            log.debug("[FCM:mock] skip — token not registered");
            return;
        }
        log.info("[FCM:mock] send token={} title='{}' body='{}'",
                truncate(fcmToken, 16), title, body);
        // TODO: FirebaseMessaging.getInstance().send(...)
    }

    private String truncate(String s, int n) {
        return s.length() > n ? s.substring(0, n) + "..." : s;
    }
}
