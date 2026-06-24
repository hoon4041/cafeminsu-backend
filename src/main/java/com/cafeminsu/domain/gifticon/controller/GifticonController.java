package com.cafeminsu.domain.gifticon.controller;

import com.cafeminsu.domain.gifticon.dto.GifticonClaimReq;
import com.cafeminsu.domain.gifticon.dto.GifticonClaimRes;
import com.cafeminsu.domain.gifticon.dto.GifticonDetailRes;
import com.cafeminsu.domain.gifticon.dto.GifticonPurchaseReq;
import com.cafeminsu.domain.gifticon.dto.GifticonPurchaseRes;
import com.cafeminsu.domain.gifticon.dto.GifticonUsageRes;
import com.cafeminsu.domain.gifticon.dto.GifticonUseReq;
import com.cafeminsu.domain.gifticon.dto.GifticonUseRes;
import com.cafeminsu.domain.gifticon.dto.MyGifticonRes;
import com.cafeminsu.domain.gifticon.dto.ReceivedGifticonRes;
import com.cafeminsu.domain.gifticon.dto.SentGifticonRes;
import com.cafeminsu.domain.gifticon.service.GifticonService;
import com.cafeminsu.global.security.LoginUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "6. Gifticon", description = "기프티콘 API")
@RestController
@RequestMapping("/api/gifticons")
@RequiredArgsConstructor
public class GifticonController {

    private final GifticonService gifticonService;

    /* 1. 기프티콘 구매 */
    @Operation(summary = "기프티콘 구매",
            description = "결제 후 발행 (현재 MVP는 결제 검증 생략, 즉시 발행). " +
                    "수신자 미지정 발행 → 응답의 claimCode/shareLink를 카카오톡으로 전달. " +
                    "receiverId/receiverPhone은 선택(하위호환).")
    @PostMapping
    public GifticonPurchaseRes purchase(@LoginUserId Long userId,
                                        @Valid @RequestBody GifticonPurchaseReq req) {
        return gifticonService.purchase(userId, req);
    }

    /* 1-2. 기프티콘 등록 (claim) */
    @Operation(summary = "기프티콘 등록(claim)",
            description = "받는 사람이 공유 링크의 claimCode로 기프티콘을 자기 계정에 귀속. " +
                    "이미 본인이 등록했으면 멱등 성공, 타인이 등록했으면 409.")
    @PostMapping("/claim")
    public GifticonClaimRes claim(@LoginUserId Long userId,
                                  @Valid @RequestBody GifticonClaimReq req) {
        return gifticonService.claim(userId, req.claimCode());
    }

    /* 2. 보낸 기프티콘 목록 */
    @Operation(summary = "보낸 기프티콘 목록")
    @GetMapping("/sent")
    public List<SentGifticonRes> sent(@LoginUserId Long userId) {
        return gifticonService.getSent(userId);
    }

    /* 3. 받은 기프티콘 목록 */
    @Operation(summary = "받은 기프티콘 목록")
    @GetMapping("/received")
    public List<ReceivedGifticonRes> received(@LoginUserId Long userId) {
        return gifticonService.getReceived(userId);
    }

    /* 4. 내 사용 가능 기프티콘 (결제 화면용) */
    @Operation(summary = "사용 가능한 내 기프티콘",
            description = "잔액 > 0, 만료 전인 것만. 결제 화면에서 호출.")
    @GetMapping("/my")
    public List<MyGifticonRes> my(@LoginUserId Long userId) {
        return gifticonService.getUsable(userId);
    }

    /* 5. 기프티콘 상세 */
    @Operation(summary = "기프티콘 상세",
            description = "본인 sender 또는 receiver만 조회 가능. 발신자는 claimCode/shareLink로 재전송 가능.")
    @GetMapping("/{gifticonId}")
    public GifticonDetailRes detail(@LoginUserId Long userId,
                                    @PathVariable Long gifticonId) {
        return gifticonService.getDetail(userId, gifticonId);
    }

    /* 6. 기프티콘 사용 (차감) */
    @Operation(summary = "기프티콘 차감",
            description = "주문 결제 시 호출. 비관적 락으로 동시 차감 방지.")
    @PostMapping("/{gifticonId}/use")
    public GifticonUseRes use(@PathVariable Long gifticonId,
                              @Valid @RequestBody GifticonUseReq req) {
        return gifticonService.use(gifticonId, req);
    }

    /* 7. 사용 내역 */
    @Operation(summary = "기프티콘 사용 내역")
    @GetMapping("/{gifticonId}/usages")
    public List<GifticonUsageRes> usages(@LoginUserId Long userId,
                                         @PathVariable Long gifticonId) {
        return gifticonService.getUsages(userId, gifticonId);
    }
}
