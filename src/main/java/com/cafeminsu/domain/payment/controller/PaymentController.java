package com.cafeminsu.domain.payment.controller;

import com.cafeminsu.domain.payment.dto.PaymentDetailRes;
import com.cafeminsu.domain.payment.dto.PaymentPrepareReq;
import com.cafeminsu.domain.payment.dto.PaymentPrepareRes;
import com.cafeminsu.domain.payment.dto.PaymentVerifyReq;
import com.cafeminsu.domain.payment.dto.PaymentVerifyRes;
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
            description = "포트원 결제창 호출 전 단계. 분할결제(gifticon+card) 지원. " +
                    "응답의 merchantUid를 포트원 SDK에 전달하세요.")
    @PostMapping("/api/payments/prepare")
    public PaymentPrepareRes prepare(@LoginUserId Long userId,
                                     @Valid @RequestBody PaymentPrepareReq req) {
        return paymentService.prepare(userId, req);
    }

    /* 2. 결제 검증 */
    @Operation(summary = "결제 검증",
            description = "포트원 결제 완료 후 콜백. impUid로 서버가 다시 조회해 금액을 검증합니다.")
    @PostMapping("/api/payments/verify")
    public PaymentVerifyRes verify(@LoginUserId Long userId,
                                   @Valid @RequestBody PaymentVerifyReq req) {
        return paymentService.verify(userId, req);
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
}
