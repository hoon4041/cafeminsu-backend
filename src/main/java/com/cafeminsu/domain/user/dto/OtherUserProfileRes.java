package com.cafeminsu.domain.user.dto;

import com.cafeminsu.domain.user.entity.User;

/**
 * 타인의 프로필 조회 응답. role은 노출하지 않음.
 */
public record OtherUserProfileRes(
        Long id,
        String nickname,
        String profileImageUrl
) {
    public static OtherUserProfileRes from(User user) {
        return new OtherUserProfileRes(
                user.getId(),
                user.getNickname(),
                user.getProfileImageUrl()
        );
    }
}
