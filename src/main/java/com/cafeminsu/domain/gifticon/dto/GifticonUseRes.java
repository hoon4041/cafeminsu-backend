package com.cafeminsu.domain.gifticon.dto;

import com.cafeminsu.domain.gifticon.entity.GifticonStatus;

public record GifticonUseRes(
        Integer balanceAfter,
        GifticonStatus status
) {
}
