package com.cafeminsu.domain.payment.dto;

/**
 * 카카오페이 ready 호출에 필요한 정보.
 *
 * - merchantUid: 우리 쪽 결제 식별자. 카카오페이 ready/approve에 그대로 전달.
 * - amount: 실제 카드로 결제할 금액 (분할결제면 cardAmount).
 *           전액 기프티콘이면 0 — 그 경우 카카오페이 호출 없이 바로 verify.
 */
public record PaymentPrepareRes(
        String merchantUid,
        Integer amount
) {
}
