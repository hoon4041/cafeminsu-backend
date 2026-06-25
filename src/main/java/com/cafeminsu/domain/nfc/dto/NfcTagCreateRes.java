package com.cafeminsu.domain.nfc.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 태그 등록 결과.
 *
 * {@code code}/{@code tagUrl}을 실제 NFC 태그에 기록해서 매장에 부착한다.
 * code는 시크릿이므로 등록 직후 한 번만 노출된다(이후 조회 API로는 내려주지 않음).
 */
public record NfcTagCreateRes(

        @Schema(description = "태그 id", example = "10")
        Long tagId,

        @Schema(description = "태그에 기록할 시크릿 코드", example = "NFC-AB7K-9QM2")
        String code,

        @Schema(description = "태그에 기록할 URL(앱 딥링크 진입용)",
                example = "https://cafeminsu.example/nfc?code=NFC-AB7K-9QM2")
        String tagUrl
) {
}
