package com.cafeminsu.domain.store.dto;

import com.cafeminsu.domain.store.entity.Store;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * GET /api/stores 응답.
 * { stores: [...], total: N }
 */
public record StoreSearchRes(
        List<StoreSearchItem> stores,
        long total
) {
    public static StoreSearchRes from(Page<Store> page) {
        List<StoreSearchItem> items = page.getContent().stream()
                .map(StoreSearchItem::from)
                .toList();
        return new StoreSearchRes(items, page.getTotalElements());
    }

    public record StoreSearchItem(
            Long id,
            String name,
            String address,
            String imageUrl
    ) {
        public static StoreSearchItem from(Store s) {
            return new StoreSearchItem(s.getId(), s.getName(), s.getAddress(), s.getImageUrl());
        }
    }
}
