package com.cafeminsu.domain.gifticon.dto;

import com.cafeminsu.domain.gifticon.entity.Gifticon;
import com.cafeminsu.domain.gifticon.entity.GifticonStatus;

public record ReceivedGifticonRes(
        Long gifticonId,
        Integer amount,
        Integer balance,
        String senderNickname,
        GifticonStatus status
) {
    public static ReceivedGifticonRes of(Gifticon g, String senderNickname) {
        return new ReceivedGifticonRes(g.getId(), g.getAmount(), g.getBalance(),
                senderNickname, g.getStatus());
    }
}
