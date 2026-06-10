package com.cafeminsu.domain.stamp.dto;

import com.cafeminsu.domain.stamp.entity.Stamp;

public record StampSummaryRes(
        Long storeId,
        String storeName,
        Integer count
) {
    public static StampSummaryRes of(Stamp s, String storeName) {
        return new StampSummaryRes(s.getStoreId(), storeName, s.getCount());
    }
}
