package com.cafeminsu.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 점주 로그인 응답. 카카오 로그인과 동일한 형태의 JWT를 발급한다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OwnerLoginRes(
        String accessToken,
        String refreshToken,
        String nickname
) {
}
