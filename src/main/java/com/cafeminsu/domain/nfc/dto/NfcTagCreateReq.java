package com.cafeminsu.domain.nfc.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 점주가 매장에 부착할 NFC 태그를 등록할 때 보내는 요청. */
public record NfcTagCreateReq(

        @Schema(description = "태그를 부착할 매장 id", example = "1")
        @NotNull Long storeId,

        @Schema(description = "태깅 1회당 발급할 쿠폰 금액(원)", example = "1000")
        @NotNull @Min(1) Integer rewardAmount,

        @Schema(description = "쿠폰에 표기할 문구(선택)", example = "방문 감사 쿠폰")
        @Size(max = 200) String message
) {
}
