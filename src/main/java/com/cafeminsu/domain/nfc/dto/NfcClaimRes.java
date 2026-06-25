package com.cafeminsu.domain.nfc.dto;

import com.cafeminsu.domain.gifticon.entity.Gifticon;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** 태깅으로 발급된 쿠폰(기프티콘) 정보. */
public record NfcClaimRes(

        @Schema(description = "발급된 기프티콘 id", example = "123")
        Long gifticonId,

        @Schema(description = "쿠폰 금액(원)", example = "1000")
        int amount,

        @Schema(description = "유효기한")
        LocalDateTime expiresAt,

        @Schema(description = "쿠폰 문구", example = "방문 감사 쿠폰")
        String message
) {
    public static NfcClaimRes from(Gifticon g) {
        return new NfcClaimRes(g.getId(), g.getAmount(), g.getExpiresAt(), g.getMessage());
    }
}
