package com.cafeminsu.domain.user.dto;

import com.cafeminsu.domain.user.entity.User;
import com.cafeminsu.domain.user.entity.UserRole;

public record UserProfileRes(
        Long id,
        String nickname,
        String profileImageUrl,
        UserRole role
) {
    public static UserProfileRes from(User user) {
        return new UserProfileRes(
                user.getId(),
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getRole()
        );
    }
}
