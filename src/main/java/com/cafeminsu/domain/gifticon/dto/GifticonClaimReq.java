package com.cafeminsu.domain.gifticon.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 기프티콘 등록(claim) 요청.
 * 공유 링크의 code 파라미터 또는 수동 입력값을 그대로 전달한다.
 */
public record GifticonClaimReq(
        @NotBlank(message = "claimCode는 필수입니다.")
        String claimCode
) {
}
