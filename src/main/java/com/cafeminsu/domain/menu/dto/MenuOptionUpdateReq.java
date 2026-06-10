package com.cafeminsu.domain.menu.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record MenuOptionUpdateReq(
        @Size(max = 30) String optionGroup,
        @Size(max = 50) String optionName,
        @Min(value = 0) Integer additionalPrice,
        Boolean isDefault
) {
}
