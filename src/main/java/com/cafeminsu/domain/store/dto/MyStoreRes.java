package com.cafeminsu.domain.store.dto;

import com.cafeminsu.domain.store.entity.Store;

/**
 * GET /api/stores/my 응답 항목.
 * 매장 앱 진입 화면에서 매장 목록 그릴 때 사용.
 */
public record MyStoreRes(
        Long id,
        String name,
        String imageUrl
) {
    public static MyStoreRes from(Store s) {
        return new MyStoreRes(s.getId(), s.getName(), s.getImageUrl());
    }
}
