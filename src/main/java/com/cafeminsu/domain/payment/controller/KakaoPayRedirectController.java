package com.cafeminsu.domain.payment.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 카카오페이 → 앱 딥링크 중계(브리지).
 *
 * 신규 오픈API는 approval/cancel/fail URL이 콘솔에 등록된 사이트도메인을 포함한 https URL이어야 한다.
 * 커스텀 스킴(cafeminsu://)을 approval_url로 직접 줄 수 없으므로, 카카오페이는 이 https 엔드포인트로
 * 리다이렉트하고, 여기서 다시 앱 딥링크(cafeminsu://kakaopay?pg_token=...)로 302 리다이렉트한다.
 *
 * 인증 헤더 없는 브라우저 리다이렉트로 호출되므로 SecurityConfig에서 permitAll로 열어야 한다.
 * 결제 승인(approve)은 앱이 딥링크에서 pg_token을 꺼내 JWT를 달고 호출하는 기존 흐름을 그대로 사용한다.
 */
@Tag(name = "5. Payment", description = "결제 API")
@RestController
public class KakaoPayRedirectController {

    private static final String APP_DEEPLINK = "cafeminsu://kakaopay";

    /** 결제 인증 완료 → 카카오페이가 pg_token을 붙여 호출 → 앱 딥링크로 302. */
    @Operation(summary = "카카오페이 인증 완료 리다이렉트(브라우저용)")
    @GetMapping("/api/payments/kakaopay/return")
    public ResponseEntity<Void> approveReturn(@RequestParam("pg_token") String pgToken) {
        String target = APP_DEEPLINK + "?pg_token="
                + URLEncoder.encode(pgToken, StandardCharsets.UTF_8);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(target)).build();
    }

    /** 사용자가 결제 취소 → 앱 딥링크로 복귀(pg_token 없음 → 앱이 취소 처리). */
    @Operation(summary = "카카오페이 결제 취소 리다이렉트(브라우저용)")
    @GetMapping("/api/payments/kakaopay/cancel")
    public ResponseEntity<Void> cancelReturn() {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(APP_DEEPLINK)).build();
    }

    /** 결제 실패 → 앱 딥링크로 복귀(pg_token 없음). */
    @Operation(summary = "카카오페이 결제 실패 리다이렉트(브라우저용)")
    @GetMapping("/api/payments/kakaopay/fail")
    public ResponseEntity<Void> failReturn() {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(APP_DEEPLINK)).build();
    }
}
