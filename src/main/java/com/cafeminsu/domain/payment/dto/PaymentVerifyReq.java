package com.cafeminsu.domain.payment.dto;

import jakarta.validation.constraints.NotBlank;

public record PaymentVerifyReq(
        @NotBlank String impUid,
        @NotBlank String merchantUid
) {
}
