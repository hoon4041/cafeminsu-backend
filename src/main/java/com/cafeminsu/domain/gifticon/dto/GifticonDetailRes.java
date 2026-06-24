package com.cafeminsu.domain.gifticon.dto;

import com.cafeminsu.domain.gifticon.entity.Gifticon;
import com.cafeminsu.domain.gifticon.entity.GifticonStatus;

import java.time.LocalDateTime;

/**
 * 기프티콘 상세.
 * 발신자는 claimCode/shareLink로 링크를 다시 받아 재전송할 수 있다.
 */
public record GifticonDetailRes(
        Long gifticonId,
        Integer amount,
        Integer balance,
        String claimCode,
        String shareLink,
        GifticonStatus status,
        LocalDateTime expiresAt,
        String message
) {
    public static GifticonDetailRes of(Gifticon g, String shareLink) {
        return new GifticonDetailRes(
                g.getId(), g.getAmount(), g.getBalance(),
                g.getClaimToken(), shareLink, g.getStatus(), g.getExpiresAt(), g.getMessage()
        );
    }
}
