package com.cafeminsu.domain.nfc.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 손님이 NFC를 태깅했을 때 앱이 읽은 태그 코드로 쿠폰 발급을 요청. */
public record NfcClaimReq(

        @Schema(description = "태그에서 읽은 코드", example = "NFC-AB7K-9QM2")
        @NotBlank String tagCode
) {
}
