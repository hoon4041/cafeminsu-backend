package com.cafeminsu.domain.stamp.dto;

import com.cafeminsu.domain.stamp.entity.Stamp;
import com.cafeminsu.domain.stamp.entity.StampHistory;

import java.time.LocalDateTime;
import java.util.List;

public record StampDetailRes(
        Long storeId,
        String storeName,
        Integer count,
        List<HistoryItem> histories
) {
    public static StampDetailRes of(Stamp s, String storeName, List<StampHistory> histories) {
        return new StampDetailRes(
                s.getStoreId(), storeName, s.getCount(),
                histories.stream().map(HistoryItem::from).toList()
        );
    }

    public record HistoryItem(Integer earnedCount, LocalDateTime createdAt) {
        static HistoryItem from(StampHistory h) {
            return new HistoryItem(h.getEarnedCount(), h.getCreatedAt());
        }
    }
}
