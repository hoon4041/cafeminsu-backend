package com.cafeminsu.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Kakao /v2/user/me 응답 일부만 파싱.
 *
 * 실제 응답 예시:
 * {
 *   "id": 12345678,
 *   "kakao_account": {
 *     "email": "user@example.com",
 *     "profile": {
 *       "nickname": "민수",
 *       "profile_image_url": "http://..."
 *     }
 *   }
 * }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoUserInfo(
        Long id,
        @JsonProperty("kakao_account") KakaoAccount kakaoAccount
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KakaoAccount(
            String email,
            Profile profile
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Profile(
            String nickname,
            @JsonProperty("profile_image_url") String profileImageUrl
    ) {
    }

    public String getKakaoIdAsString() {
        return String.valueOf(id);
    }

    public String getEmail() {
        return kakaoAccount != null ? kakaoAccount.email() : null;
    }

    public String getProfileImageUrl() {
        if (kakaoAccount == null || kakaoAccount.profile() == null) return null;
        return kakaoAccount.profile().profileImageUrl();
    }
}
