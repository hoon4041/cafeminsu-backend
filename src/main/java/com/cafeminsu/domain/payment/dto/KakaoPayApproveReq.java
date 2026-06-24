package com.cafeminsu.domain.payment.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * POST /api/payments/kakaopay/approve 요청.
 * pgToken은 카카오페이 인증 후 딥링크(cafeminsu://kakaopay?pg_token=...)에서 추출한 값.
 */
public record KakaoPayApproveReq(
        @NotBlank String tid,
        @NotBlank String pgToken,
        @NotBlank String merchantUid
) {
}
