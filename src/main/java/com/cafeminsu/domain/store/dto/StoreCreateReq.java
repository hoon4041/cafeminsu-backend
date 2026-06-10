package com.cafeminsu.domain.store.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record StoreCreateReq(
        @NotBlank(message = "매장명은 필수입니다.")
        @Size(max = 100, message = "매장명은 100자 이내여야 합니다.")
        String name,

        @NotBlank(message = "주소는 필수입니다.")
        @Size(max = 200, message = "주소는 200자 이내여야 합니다.")
        String address,

        @NotNull
        @DecimalMin(value = "-90.0") @DecimalMax(value = "90.0")
        BigDecimal latitude,

        @NotNull
        @DecimalMin(value = "-180.0") @DecimalMax(value = "180.0")
        BigDecimal longitude,

        @Size(max = 30, message = "전화번호는 30자 이내여야 합니다.")
        String phone,

        @Size(max = 100, message = "영업시간은 100자 이내여야 합니다.")
        String businessHours,

        @Size(max = 500)
        String imageUrl
) {
}
