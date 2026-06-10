package com.cafeminsu.domain.gifticon.dto;

import com.cafeminsu.domain.gifticon.entity.Gifticon;

import java.time.LocalDateTime;

/**
 * 결제 화면에서 보여줄 사용 가능 기프티콘.
 * balance > 0, 만료 전인 것만.
 */
public record MyGifticonRes(
        Long gifticonId,
        Integer balance,
        LocalDateTime expiresAt
) {
    public static MyGifticonRes from(Gifticon g) {
        return new MyGifticonRes(g.getId(), g.getBalance(), g.getExpiresAt());
    }
}
