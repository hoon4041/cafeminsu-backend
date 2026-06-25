package com.cafeminsu.domain.nfc.controller;

import com.cafeminsu.domain.nfc.dto.NfcClaimReq;
import com.cafeminsu.domain.nfc.dto.NfcClaimRes;
import com.cafeminsu.domain.nfc.dto.NfcTagCreateReq;
import com.cafeminsu.domain.nfc.dto.NfcTagCreateRes;
import com.cafeminsu.domain.nfc.service.NfcService;
import com.cafeminsu.global.security.LoginUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "8. NFC", description = "NFC 태깅 쿠폰 API")
@RestController
@RequestMapping("/api/nfc")
@RequiredArgsConstructor
public class NfcController {

    private final NfcService nfcService;

    /* 1. 태그 등록 (점주 전용 — SecurityConfig에서 OWNER 차단) */
    @Operation(summary = "NFC 태그 등록",
            description = "점주 본인 매장에 부착할 태그를 등록합니다. 응답의 code/tagUrl을 태그에 기록하세요. "
                    + "code는 이 응답에서만 노출됩니다.")
    @PostMapping("/tags")
    public NfcTagCreateRes createTag(@LoginUserId Long userId,
                                     @Valid @RequestBody NfcTagCreateReq req) {
        return nfcService.createTag(userId, req);
    }

    /* 2. 태깅 → 쿠폰 발급 (손님) */
    @Operation(summary = "NFC 태깅 쿠폰 발급",
            description = "앱이 읽은 태그 코드로 쿠폰(기프티콘)을 발급합니다. 태그당 하루 1회.")
    @PostMapping("/claim")
    public NfcClaimRes claim(@LoginUserId Long userId,
                             @Valid @RequestBody NfcClaimReq req) {
        return nfcService.claim(userId, req.tagCode());
    }
}
