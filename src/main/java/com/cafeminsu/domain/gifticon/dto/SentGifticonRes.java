package com.cafeminsu.domain.gifticon.dto;

import com.cafeminsu.domain.gifticon.entity.Gifticon;
import com.cafeminsu.domain.gifticon.entity.GifticonStatus;

public record SentGifticonRes(
        Long gifticonId,
        Integer amount,
        Integer balance,
        String receiverNickname,    // 수신자 닉네임 (미가입자면 null)
        GifticonStatus status
) {
    public static SentGifticonRes of(Gifticon g, String receiverNickname) {
        return new SentGifticonRes(g.getId(), g.getAmount(), g.getBalance(),
                receiverNickname, g.getStatus());
    }
}
