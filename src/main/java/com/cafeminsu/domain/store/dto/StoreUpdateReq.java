package com.cafeminsu.domain.store.dto;

import jakarta.validation.constraints.Size;

/**
 * PATCH 부분 수정 — 모든 필드 옵셔널. null이면 기존 값 유지.
 * 위치(latitude/longitude)는 수정 대상에서 제외(매장 이전은 새 매장 등록으로 처리).
 */
public record StoreUpdateReq(
        @Size(max = 100) String name,
        @Size(max = 200) String address,
        @Size(max = 30) String phone,
        @Size(max = 100) String businessHours,
        @Size(max = 500) String imageUrl
) {
}
