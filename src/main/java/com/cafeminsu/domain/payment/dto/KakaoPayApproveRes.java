package com.cafeminsu.domain.payment.dto;

/**
 * POST /api/payments/kakaopay/approve 응답.
 * paymentToken은 이후 기존 verify의 impUid 슬롯에 그대로 들어간다(카카오페이 aid).
 */
public record KakaoPayApproveRes(
        String paymentToken
) {
}
