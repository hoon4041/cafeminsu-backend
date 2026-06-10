package com.cafeminsu.domain.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 카카오 SDK로 받은 액세스 토큰을 서버에 전달.
 */
public record KakaoLoginReq(
        @NotBlank(message = "accessToken은 필수입니다.")
        String accessToken
) {
}
