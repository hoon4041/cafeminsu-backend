package com.cafeminsu.domain.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

/**
 * 결제 준비.
 *
 * 분할 금액은 서버가 계산한다(클라가 보내지 않는다).
 *  - useGifticonId 지정 시: 기프티콘 차감액 = min(기프티콘 잔액, 주문 총액), 카드액 = 나머지
 *  - useGifticonId 미지정 시: 전액 카드
 *
 * (구버전 앱이 gifticonAmount/cardAmount를 함께 보내도 무시한다.)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentPrepareReq(
        @NotNull(message = "orderId는 필수입니다.")
        Long orderId,

        Long useGifticonId
) {
}
