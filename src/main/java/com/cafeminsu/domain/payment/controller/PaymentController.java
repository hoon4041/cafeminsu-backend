package com.cafeminsu.domain.payment.controller;

import com.cafeminsu.domain.payment.dto.KakaoPayApproveReq;
import com.cafeminsu.domain.payment.dto.KakaoPayApproveRes;
import com.cafeminsu.domain.payment.dto.KakaoPayReadyReq;
import com.cafeminsu.domain.payment.dto.KakaoPayReadyRes;
import com.cafeminsu.domain.payment.dto.PaymentDetailRes;
import com.cafeminsu.domain.payment.dto.PaymentPrepareReq;
import com.cafeminsu.domain.payment.dto.PaymentPrepareRes;
import com.cafeminsu.domain.payment.dto.PaymentVerifyReq;
import com.cafeminsu.domain.payment.dto.PaymentVerifyRes;
import com.cafeminsu.domain.payment.dto.SalesSummaryRes;
import com.cafeminsu.domain.payment.dto.StorePaymentsRes;
import com.cafeminsu.domain.payment.service.PaymentService;
import com.cafeminsu.global.security.LoginUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "5. Payment", description = "결제 API")
@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /* 1. 결제 준비 */
    @Operation(summary = "결제 준비",
            description = "orderId(+useGifticonId)만 보내면 서버가 분할 금액을 계산합니다. " +
                    "응답 status=READY면 카드 결제분이 있어 cardAmount로 카카오페이 ready를 호출, " +
                    "status=PAID면 전액 기프티콘으로 즉시 확정된 것(카카오페이·verify 불필요).")
    @PostMapping("/api/payments/prepare")
    public PaymentPrepareRes prepare(@LoginUserId Long userId,
                                     @Valid @RequestBody PaymentPrepareReq req) {
        return paymentService.prepare(userId, req);
    }

    /* 2. 결제 검증 (카드 결제 확정 전용) */
    @Operation(summary = "결제 검증 (카드)",
            description = "카드 결제 확정 전용. 카카오페이 approve 응답의 paymentToken을 impUid 슬롯에 넣어 호출하면 " +
                    "서버가 approve 때 저장한 승인번호와 대조해 확정합니다(분할결제면 기프티콘분도 함께 차감). " +
                    "전액 기프티콘은 prepare에서 이미 확정되므로 호출하지 않습니다.")
    @PostMapping("/api/payments/verify")
    public PaymentVerifyRes verify(@LoginUserId Long userId,
                                   @Valid @RequestBody PaymentVerifyReq req) {
        return paymentService.verify(userId, req);
    }

    /* 2-1. 카카오페이 ready */
    @Operation(summary = "카카오페이 결제 준비(ready)",
            description = "prepare로 발급된 merchantUid + 카드 결제분 amount를 보냅니다. " +
                    "응답의 redirectUrl을 외부 브라우저로 열어 사용자 인증을 진행하세요.")
    @PostMapping("/api/payments/kakaopay/ready")
    public KakaoPayReadyRes kakaoPayReady(@LoginUserId Long userId,
                                          @Valid @RequestBody KakaoPayReadyReq req) {
        return paymentService.kakaoPayReady(userId, req);
    }

    /* 2-2. 카카오페이 approve */
    @Operation(summary = "카카오페이 결제 승인(approve)",
            description = "딥링크에서 추출한 pgToken으로 승인합니다. 응답의 paymentToken을 이후 verify의 impUid로 사용하세요.")
    @PostMapping("/api/payments/kakaopay/approve")
    public KakaoPayApproveRes kakaoPayApprove(@LoginUserId Long userId,
                                              @Valid @RequestBody KakaoPayApproveReq req) {
        return paymentService.kakaoPayApprove(userId, req);
    }

    /* 3. 결제 상세 */
    @Operation(summary = "결제 상세")
    @GetMapping("/api/payments/{paymentId}")
    public PaymentDetailRes detail(@LoginUserId Long userId,
                                   @PathVariable Long paymentId) {
        return paymentService.getDetail(userId, paymentId);
    }

    /* 4. 매장 결제 내역 (점주, 정산용) */
    @Operation(summary = "매장 결제 내역 (점주)",
            description = "기간 내 PAID 결제 합계와 목록. 정산 화면 데이터.")
    @GetMapping("/api/stores/{storeId}/payments")
    public StorePaymentsRes storePayments(
            @LoginUserId Long userId,
            @PathVariable Long storeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return paymentService.getStorePayments(userId, storeId, from, to);
    }

    /* 5. 매장 매출 요약 (점주, 대시보드) */
    @Operation(summary = "매장 매출 요약 (점주)",
            description = "기간 내 PAID 결제 기준 총매출·일자별 매출·메뉴별 판매 랭킹. 대시보드 데이터.")
    @GetMapping("/api/stores/{storeId}/sales-summary")
    public SalesSummaryRes salesSummary(
            @LoginUserId Long userId,
            @PathVariable Long storeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return paymentService.getSalesSummary(userId, storeId, from, to);
    }
}
