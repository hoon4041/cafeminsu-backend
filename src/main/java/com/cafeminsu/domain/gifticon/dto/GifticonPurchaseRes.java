package com.cafeminsu.domain.gifticon.dto;

public record GifticonPurchaseRes(
        Long gifticonId,
        String qrCode,
        String merchantUid    // 포트원 결제 호출용 (TODO: 실제 결제 연동)
) {
}
