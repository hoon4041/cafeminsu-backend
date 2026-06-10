package com.cafeminsu.domain.menu.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

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
        Boolean isAvailable
) {
}
