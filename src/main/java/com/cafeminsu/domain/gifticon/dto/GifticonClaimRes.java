package com.cafeminsu.domain.gifticon.dto;

import com.cafeminsu.domain.gifticon.entity.Gifticon;
import com.cafeminsu.domain.gifticon.entity.GifticonStatus;

import java.time.LocalDateTime;

/**
 * 기프티콘 등록(claim) 응답.
 * 등록 직후 받는 사람에게 보여줄 기프티콘 정보. 이후 GET /api/gifticons/my 에도 노출된다.
 */
public record GifticonClaimRes(
        Long gifticonId,
        String title,
        Integer amount,
        Integer balance,
        GifticonStatus status,
        LocalDateTime expiresAt,
        String message
) {
    public static GifticonClaimRes from(Gifticon g) {
        return new GifticonClaimRes(
                g.getId(),
                "금액형 기프티콘 %,d원".formatted(g.getAmount()),
                g.getAmount(),
                g.getBalance(),
                g.getStatus(),
                g.getExpiresAt(),
                g.getMessage()
        );
    }
}
