package com.cafeminsu.domain.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 점주 ID/PW 로그인 요청.
 * 계정은 DB에 사전 등록되어 있으며(비밀번호는 BCrypt 해시), 여기서 평문을 받아 대조한다.
 */
public record OwnerLoginReq(
        @NotBlank(message = "loginId는 필수입니다.")
        String loginId,

        @NotBlank(message = "password는 필수입니다.")
        String password
) {
}
