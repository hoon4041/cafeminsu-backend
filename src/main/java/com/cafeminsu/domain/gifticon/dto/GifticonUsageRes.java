package com.cafeminsu.domain.gifticon.dto;

import com.cafeminsu.domain.gifticon.entity.GifticonUsage;

import java.time.LocalDateTime;

public record GifticonUsageRes(
        Integer usedAmount,
        Integer balanceAfter,
        String storeName,
        LocalDateTime usedAt
) {
    public static GifticonUsageRes of(GifticonUsage u, String storeName) {
        return new GifticonUsageRes(
                u.getUsedAmount(), u.getBalanceAfter(),
                storeName, u.getUsedAt()
        );
    }
}
