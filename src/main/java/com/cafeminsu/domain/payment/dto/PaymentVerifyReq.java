package com.cafeminsu.domain.payment.dto;

import jakarta.validation.constraints.NotBlank;

public record PaymentVerifyReq(
        /** 카카오페이 approve 응답의 paymentToken(=승인번호 aid)을 그대로 넣는다. */
        @NotBlank String impUid,
        @NotBlank String merchantUid
) {
}
