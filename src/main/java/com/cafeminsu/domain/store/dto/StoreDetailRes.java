package com.cafeminsu.domain.store.dto;

import com.cafeminsu.domain.store.entity.Store;

import java.math.BigDecimal;

public record StoreDetailRes(
        Long id,
        String name,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        String phone,
        String businessHours,
        String imageUrl
) {
    public static StoreDetailRes from(Store s) {
        return new StoreDetailRes(
                s.getId(),
                s.getName(),
                s.getAddress(),
                s.getLatitude(),
                s.getLongitude(),
                s.getPhone(),
                s.getBusinessHours(),
                s.getImageUrl()
        );
    }
}
