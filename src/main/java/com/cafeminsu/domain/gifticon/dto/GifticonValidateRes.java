package com.cafeminsu.domain.gifticon.dto;

public record GifticonValidateRes(
        Long gifticonId,
        Integer balance,
        String ownerNickname,
        boolean isValid
) {
}
