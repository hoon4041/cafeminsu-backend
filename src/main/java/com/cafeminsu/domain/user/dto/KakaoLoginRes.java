package com.cafeminsu.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record KakaoLoginRes(
        String accessToken,
        String refreshToken,
        boolean isNewUser,
        String nickname
) {
}
