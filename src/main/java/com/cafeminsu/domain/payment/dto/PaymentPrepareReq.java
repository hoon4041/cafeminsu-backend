package com.cafeminsu.domain.payment.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 결제 준비.
 *
 * 분할결제: gifticonAmount + cardAmount = order.totalAmount
 * 전액 카드: useGifticonId, gifticonAmount 생략. cardAmount만.
 * 전액 기프티콘: cardAmount = 0.
 */
public record PaymentPrepareReq(
        @NotNull(message = "orderId는 필수입니다.")
        Long orderId,

        Long useGifticonId,

        @Min(value = 0, message = "gifticonAmount는 0 이상이어야 합니다.")
        Integer gifticonAmount,

        @Min(value = 0, message = "cardAmount는 0 이상이어야 합니다.")
        Integer cardAmount
) {
}
