package com.cafeminsu.domain.store.dto;

import com.cafeminsu.domain.store.repository.StoreRepository.NearbyStoreProjection;

/**
 * GET /api/stores/nearby 응답 항목.
 * distance: 미터 단위 (정수로 라운드)
 */
public record NearbyStoreRes(
        Long id,
        String name,
        Long distance,
        String imageUrl
) {
    public static NearbyStoreRes from(NearbyStoreProjection p) {
        long meters = p.getDistance() == null ? 0L : Math.round(p.getDistance());
        return new NearbyStoreRes(p.getId(), p.getName(), meters, p.getImageUrl());
    }
}
