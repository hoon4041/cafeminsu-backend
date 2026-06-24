package com.cafeminsu.domain.payment.dto;

/**
 * POST /api/payments/kakaopay/ready 응답.
 * 앱은 redirectUrl을 외부 브라우저로 열어 사용자 인증을 진행한다.
 */
public record KakaoPayReadyRes(
        String tid,
        String redirectUrl
) {
}
