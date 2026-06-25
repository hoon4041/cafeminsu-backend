package com.cafeminsu.domain.payment.dto;

import com.cafeminsu.domain.payment.entity.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 결제 준비 응답. 서버가 계산한 분할 금액과 진행 방식을 함께 내려준다.
 *
 * - merchantUid: 우리 쪽 결제 식별자.
 * - gifticonAmount / cardAmount: 서버가 계산한 분할 금액.
 * - status:
 *     READY — 카드 결제분이 있어 카카오페이(ready→approve→verify) 진행이 필요.
 *     PAID  — 전액 기프티콘이라 prepare 단계에서 즉시 확정됨. 추가 호출 불필요.
 * - paymentId: 전액 기프티콘으로 즉시 확정된 경우의 결제 ID (그 외 null).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentPrepareRes(
        String merchantUid,
        Integer gifticonAmount,
        Integer cardAmount,
        PaymentStatus status,
        Long paymentId
) {
}
