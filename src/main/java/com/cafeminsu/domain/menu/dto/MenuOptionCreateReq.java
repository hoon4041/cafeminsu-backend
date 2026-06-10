package com.cafeminsu.domain.menu.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MenuOptionCreateReq(
        @NotBlank(message = "optionGroup은 필수입니다.")
        @Size(max = 30)
        String optionGroup,

        @NotBlank(message = "optionName은 필수입니다.")
        @Size(max = 50)
        String optionName,

        @NotNull
        @Min(value = 0, message = "추가 금액은 0원 이상이어야 합니다.")
        Integer additionalPrice,

        /** null이면 false */
        Boolean isDefault
) {
}
