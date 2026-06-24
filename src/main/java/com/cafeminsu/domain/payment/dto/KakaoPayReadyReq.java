package com.cafeminsu.domain.payment.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * POST /api/payments/kakaopay/ready 요청.
 * merchantUid는 사전 prepare에서 발급된 값, amount는 그 카드 결제분 금액.
 */
public record KakaoPayReadyReq(
        @NotBlank String merchantUid,
        @NotNull @Min(1) Integer amount
) {
}
