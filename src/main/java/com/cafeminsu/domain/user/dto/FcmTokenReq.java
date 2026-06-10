package com.cafeminsu.domain.user.dto;

import jakarta.validation.constraints.NotBlank;

public record FcmTokenReq(
        @NotBlank(message = "fcmToken은 필수입니다.")
        String fcmToken
) {
}
