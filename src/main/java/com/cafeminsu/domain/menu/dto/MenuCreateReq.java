package com.cafeminsu.domain.menu.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record MenuCreateReq(
        @NotBlank(message = "메뉴명은 필수입니다.")
        @Size(max = 100)
        String name,

        @Size(max = 500)
        String description,

        @NotNull(message = "가격은 필수입니다.")
        @Min(value = 0, message = "가격은 0원 이상이어야 합니다.")
        Integer price,

        @Size(max = 50)
        String category,

        @Size(max = 500)
        String imageUrl,

        /** null이면 기본 true */
        Boolean isAvailable,

        /**
         * 메뉴와 함께 등록할 옵션 목록. 선택값 — null이거나 비어 있으면 옵션 없이 메뉴만 생성.
         * 등록 후 개별 추가는 POST /api/menus/{menuId}/options 사용.
         * {@code @Valid}로 각 옵션의 필드 검증이 중첩 적용된다.
         */
        @Valid
        List<MenuOptionCreateReq> options
) {
}
