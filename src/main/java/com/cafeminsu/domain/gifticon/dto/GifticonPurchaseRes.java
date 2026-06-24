package com.cafeminsu.domain.gifticon.dto;

/**
 * 기프티콘 구매 응답.
 *
 * 구매자는 shareLink(또는 claimCode)를 카카오톡 메시지/공유로 친구에게 전달하고,
 * 받는 사람이 앱에서 claim(등록)한다.
 * amount/message는 카카오톡 메시지 템플릿 구성용으로 함께 내려준다.
 */
public record GifticonPurchaseRes(
        Long gifticonId,
        String claimCode,   // 받는 사람이 등록 시 입력하는 1회성 코드 (GFT-XXXX-XXXX)
        String shareLink,   // 클레임 페이지/딥링크 URL (code 포함)
        Integer amount,
        String message
) {
}
