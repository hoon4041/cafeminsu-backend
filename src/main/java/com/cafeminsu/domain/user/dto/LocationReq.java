package com.cafeminsu.domain.user.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record LocationReq(
        @NotNull(message = "latitude는 필수입니다.")
        @DecimalMin(value = "-90.0", message = "latitude 범위는 -90 ~ 90 입니다.")
        @DecimalMax(value = "90.0", message = "latitude 범위는 -90 ~ 90 입니다.")
        BigDecimal latitude,

        @NotNull(message = "longitude는 필수입니다.")
        @DecimalMin(value = "-180.0", message = "longitude 범위는 -180 ~ 180 입니다.")
        @DecimalMax(value = "180.0", message = "longitude 범위는 -180 ~ 180 입니다.")
        BigDecimal longitude
) {
}
