package com.cafeminsu.domain.gifticon.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 기프티콘 구매 요청.
 * receiverId(회원) 또는 receiverPhone(미가입자) 중 하나는 필수.
 */
public record GifticonPurchaseReq(
        @NotNull
        @Min(value = 1000, message = "최소 금액은 1000원입니다.")
        Integer amount,

        Long receiverId,

        @Size(max = 20)
        String receiverPhone,

        @Size(max = 200)
        String message
) {
}
