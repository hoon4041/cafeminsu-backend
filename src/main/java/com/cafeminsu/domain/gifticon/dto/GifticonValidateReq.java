package com.cafeminsu.domain.gifticon.dto;

import jakarta.validation.constraints.NotBlank;

public record GifticonValidateReq(
        @NotBlank(message = "qrCode는 필수입니다.")
        String qrCode
) {
}
