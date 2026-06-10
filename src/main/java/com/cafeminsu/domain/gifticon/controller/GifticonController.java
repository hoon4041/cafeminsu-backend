package com.cafeminsu.domain.gifticon.controller;

import com.cafeminsu.domain.gifticon.dto.GifticonDetailRes;
import com.cafeminsu.domain.gifticon.dto.GifticonPurchaseReq;
import com.cafeminsu.domain.gifticon.dto.GifticonPurchaseRes;
import com.cafeminsu.domain.gifticon.dto.GifticonShareRes;
import com.cafeminsu.domain.gifticon.dto.GifticonUsageRes;
import com.cafeminsu.domain.gifticon.dto.GifticonUseReq;
import com.cafeminsu.domain.gifticon.dto.GifticonUseRes;
import com.cafeminsu.domain.gifticon.dto.GifticonValidateReq;
import com.cafeminsu.domain.gifticon.dto.GifticonValidateRes;
import com.cafeminsu.domain.gifticon.dto.MyGifticonRes;
import com.cafeminsu.domain.gifticon.dto.ReceivedGifticonRes;
import com.cafeminsu.domain.gifticon.dto.SentGifticonRes;
import com.cafeminsu.domain.gifticon.service.GifticonService;
import com.cafeminsu.global.common.BaseResponse;
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
                    "receiverId 또는 receiverPhone 중 하나는 필수.")
    @PostMapping
    public BaseResponse<GifticonPurchaseRes> purchase(@LoginUserId Long userId,
                                                      @Valid @RequestBody GifticonPurchaseReq req) {
        return BaseResponse.success(gifticonService.purchase(userId, req));
    }

    /* 2. 보낸 기프티콘 목록 */
    @Operation(summary = "보낸 기프티콘 목록")
    @GetMapping("/sent")
    public BaseResponse<List<SentGifticonRes>> sent(@LoginUserId Long userId) {
        return BaseResponse.success(gifticonService.getSent(userId));
    }

    /* 3. 받은 기프티콘 목록 */
    @Operation(summary = "받은 기프티콘 목록")
    @GetMapping("/received")
    public BaseResponse<List<ReceivedGifticonRes>> received(@LoginUserId Long userId) {
        return BaseResponse.success(gifticonService.getReceived(userId));
    }

    /* 4. 내 사용 가능 기프티콘 (결제 화면용) */
    @Operation(summary = "사용 가능한 내 기프티콘",
            description = "잔액 > 0, 만료 전인 것만. 결제 화면에서 호출.")
    @GetMapping("/my")
    public BaseResponse<List<MyGifticonRes>> my(@LoginUserId Long userId) {
        return BaseResponse.success(gifticonService.getUsable(userId));
    }

    /* 5. QR 스캔 검증 — 키오스크/매장에서 호출 */
    @Operation(summary = "QR 검증",
            description = "키오스크에서 QR 스캔 시 호출. 잔액과 유효성 확인.")
    @PostMapping("/redeem/validate")
    public BaseResponse<GifticonValidateRes> validate(@Valid @RequestBody GifticonValidateReq req) {
        return BaseResponse.success(gifticonService.validate(req.qrCode()));
    }

    /* 6. 기프티콘 상세 */
    @Operation(summary = "기프티콘 상세", description = "QR 코드 표시용. 본인 sender 또는 receiver만 조회 가능.")
    @GetMapping("/{gifticonId}")
    public BaseResponse<GifticonDetailRes> detail(@LoginUserId Long userId,
                                                  @PathVariable Long gifticonId) {
        return BaseResponse.success(gifticonService.getDetail(userId, gifticonId));
    }

    /* 7. 기프티콘 사용 (차감) */
    @Operation(summary = "기프티콘 차감",
            description = "주문 결제 시 호출. 비관적 락으로 동시 차감 방지.")
    @PostMapping("/{gifticonId}/use")
    public BaseResponse<GifticonUseRes> use(@PathVariable Long gifticonId,
                                            @Valid @RequestBody GifticonUseReq req) {
        return BaseResponse.success(gifticonService.use(gifticonId, req));
    }

    /* 8. 선물하기 (공유 링크) */
    @Operation(summary = "기프티콘 선물 링크 발급",
            description = "카카오 공유·딥링크. 현재 단순 링크, 추후 카카오 메시지 API 연동.")
    @PostMapping("/{gifticonId}/share")
    public BaseResponse<GifticonShareRes> share(@LoginUserId Long userId,
                                                @PathVariable Long gifticonId) {
        return BaseResponse.success(gifticonService.share(userId, gifticonId));
    }

    /* 9. 사용 내역 */
    @Operation(summary = "기프티콘 사용 내역")
    @GetMapping("/{gifticonId}/usages")
    public BaseResponse<List<GifticonUsageRes>> usages(@LoginUserId Long userId,
                                                       @PathVariable Long gifticonId) {
        return BaseResponse.success(gifticonService.getUsages(userId, gifticonId));
    }
}
