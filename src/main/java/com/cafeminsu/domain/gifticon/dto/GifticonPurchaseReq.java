package com.cafeminsu.domain.gifticon.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 기프티콘 구매 요청.
 *
 * 친구 선물(링크 방식)에서는 구매 시점에 수신자를 지정하지 않는다 → receiverId/receiverPhone 모두 생략 가능.
 * 받는 사람은 응답의 claimCode/shareLink로 직접 등록(claim)한다.
 * 단, 아는 회원에게 즉시 지정하거나 비회원에게 전화번호로 보내는 기존 방식도 하위호환으로 허용한다.
 */
public record GifticonPurchaseReq(
        @NotNull
        @Min(value = 1000, message = "최소 금액은 1000원입니다.")
        Integer amount,

        Long receiverId,        // (선택) 회원에게 즉시 지정

        @Size(max = 20)
        String receiverPhone,   // (선택) 비회원 전화번호

        @Size(max = 200)
        String message
) {
}
