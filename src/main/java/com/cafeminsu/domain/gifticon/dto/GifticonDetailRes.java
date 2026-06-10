package com.cafeminsu.domain.gifticon.dto;

import com.cafeminsu.domain.gifticon.entity.Gifticon;
import com.cafeminsu.domain.gifticon.entity.GifticonStatus;

import java.time.LocalDateTime;

public record GifticonDetailRes(
        Long gifticonId,
        Integer amount,
        Integer balance,
        String qrCode,
        GifticonStatus status,
        LocalDateTime expiresAt,
        String message
) {
    public static GifticonDetailRes from(Gifticon g) {
        return new GifticonDetailRes(
                g.getId(), g.getAmount(), g.getBalance(),
                g.getQrCode(), g.getStatus(), g.getExpiresAt(), g.getMessage()
        );
    }
}
