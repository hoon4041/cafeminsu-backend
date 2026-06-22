package com.cafeminsu.global.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * Firebase Admin SDK 초기화.
 *
 * fcm.service-account-path가 가리키는 서비스 계정 키(JSON)로 FirebaseApp을 초기화하고
 * FirebaseMessaging 빈을 등록한다. 키 파일은 보안상 git에 올리지 않으므로(.gitignore),
 * 키가 없는 로컬/CI 환경에서는 빈을 등록하지 않고 경고만 남긴다.
 * 이 경우 {@link com.cafeminsu.domain.notification.service.FcmClient}가 mock(로그)으로 동작한다.
 *
 * 키 탐색 순서:
 *   1) 파일 시스템 경로 (배포 시 jar 옆에 둔 firebase-service-account.json)
 *   2) classpath (개발 시 src/main/resources에 둔 경우)
 */
@Slf4j
@Configuration
public class FirebaseConfig {

    @Value("${fcm.service-account-path}")
    private String serviceAccountPath;

    @Bean
    public FirebaseMessaging firebaseMessaging() {
        try (InputStream credentials = openCredentials()) {
            if (credentials == null) {
                log.warn("[Firebase] 서비스 계정 키를 찾을 수 없습니다 (path='{}'). "
                        + "FCM은 mock으로 동작합니다.", serviceAccountPath);
                return null;
            }

            FirebaseApp app;
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(credentials))
                        .build();
                app = FirebaseApp.initializeApp(options);
                log.info("[Firebase] 초기화 완료 (path='{}')", serviceAccountPath);
            } else {
                app = FirebaseApp.getInstance();
            }
            return FirebaseMessaging.getInstance(app);
        } catch (Exception e) {
            log.warn("[Firebase] 초기화 실패 — FCM은 mock으로 동작합니다: {}", e.getMessage());
            return null;
        }
    }

    /** 파일 시스템 → classpath 순으로 키 파일을 찾는다. 없으면 null. */
    private InputStream openCredentials() throws Exception {
        File file = new File(serviceAccountPath);
        if (file.exists()) {
            return new FileInputStream(file);
        }
        ClassPathResource resource = new ClassPathResource(serviceAccountPath);
        if (resource.exists()) {
            return resource.getInputStream();
        }
        return null;
    }
}
