package com.cafeminsu.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record NicknameChangeReq(
        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(min = 2, max = 20, message = "닉네임은 2~20자여야 합니다.")
        @Pattern(regexp = "^[가-힣a-zA-Z0-9_]+$",
                message = "닉네임은 한글·영문·숫자·언더스코어만 사용할 수 있습니다.")
        String nickname
) {
}
