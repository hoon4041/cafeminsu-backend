package com.cafeminsu.domain.menu.dto;

import jakarta.validation.constraints.NotNull;

public record MenuAvailabilityReq(
        @NotNull(message = "isAvailable은 필수입니다.")
        Boolean isAvailable
) {
}
