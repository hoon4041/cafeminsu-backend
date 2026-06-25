package com.cafeminsu.domain.payment.service;

import com.cafeminsu.domain.gifticon.dto.GifticonUseReq;
import com.cafeminsu.domain.gifticon.service.GifticonService;
import com.cafeminsu.domain.order.entity.Order;
import com.cafeminsu.domain.order.entity.OrderStatus;
import com.cafeminsu.domain.order.repository.OrderItemRepository;
import com.cafeminsu.domain.order.repository.OrderRepository;
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
import com.cafeminsu.domain.payment.entity.Payment;
import com.cafeminsu.domain.payment.entity.PaymentMethod;
import com.cafeminsu.domain.payment.entity.PaymentStatus;
import com.cafeminsu.domain.payment.repository.PaymentRepository;
import com.cafeminsu.domain.store.entity.Store;
import com.cafeminsu.domain.store.repository.StoreRepository;
import com.cafeminsu.global.common.BaseResponseStatus;
import com.cafeminsu.global.exception.BaseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    /** 메뉴별 판매 랭킹 노출 개수 */
    private static final int TOP_MENU_LIMIT = 10;

    private final PaymentRepository paymentRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderRepository orderRepository;
    private final StoreRepository storeRepository;
    private final KakaoPayClient kakaoPayClient;
    private final GifticonService gifticonService;

    /* =========================================================
     * 1) 결제 준비
     *
     * - 주문 본인 확인
     * - 주문 상태가 PENDING이어야 함 (이미 결제했거나 취소된 주문은 불가)
     * - 금액 합계 검증: gifticonAmount + cardAmount == order.totalAmount
     * - 분할이면 Payment row 2개 생성, 단일이면 1개
     * - merchantUid 발급해서 응답 (카카오페이 ready 호출용)
     * ========================================================= */
    @Transactional
    public PaymentPrepareRes prepare(Long userId, PaymentPrepareReq req) {
        Order order = orderRepository.findById(req.orderId())
                .orElseThrow(() -> new BaseException(BaseResponseStatus.ORDER_NOT_FOUND));

        // 본인 주문만 결제 가능
        if (!order.isPlacedBy(userId)) {
            throw new BaseException(BaseResponseStatus.ACCESS_DENIED);
        }
        // 주문 상태 검증 — 이미 결제 완료된 주문 재결제 방지
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BaseException(BaseResponseStatus.INVALID_ORDER_STATUS);
        }
        // 이미 prepare된 결제가 있으면 차단 (멱등 처리하지 않고 명시적 실패)
        if (!paymentRepository.findAllByOrderId(order.getId()).isEmpty()) {
            throw new BaseException(BaseResponseStatus.INVALID_ORDER_STATUS,
                    "이미 결제가 준비된 주문입니다.");
        }

        // 서버가 분할 금액을 계산한다(클라가 보내지 않는다).
        // 기프티콘 차감액 = min(잔액, 주문총액), 카드액 = 나머지.
        int total = order.getTotalAmount();
        int gifticonAmount = 0;
        if (req.useGifticonId() != null) {
            // 소유·만료·사용가능 검증 후 차감액 산출(주문액 한도로 캡)
            gifticonAmount = gifticonService.resolvePaymentAmount(userId, req.useGifticonId(), total);
        }
        int cardAmount = total - gifticonAmount;

        String merchantUid = generateMerchantUid(order.getId());

        // 기프티콘 결제분 row. 전액 기프티콘이면 merchantUid를 여기에 저장(추적·조회용).
        Payment gifticonPayment = null;
        if (gifticonAmount > 0) {
            gifticonPayment = paymentRepository.save(Payment.builder()
                    .orderId(order.getId())
                    .merchantUid(cardAmount == 0 ? merchantUid : null)
                    .amount(gifticonAmount)
                    .method(PaymentMethod.GIFTICON)
                    .gifticonId(req.useGifticonId())
                    .build());
        }
        // 카드 결제분 row — merchantUid는 카드 row에
        if (cardAmount > 0) {
            paymentRepository.save(Payment.builder()
                    .orderId(order.getId())
                    .merchantUid(merchantUid)
                    .amount(cardAmount)
                    .method(PaymentMethod.CARD)
                    .build());
        }

        // 전액 기프티콘 — 카드 결제분이 없으니 카카오페이가 불필요하다.
        // prepare 단계에서 즉시 잔액을 차감하고 PAID로 확정한다(verify 호출 불필요).
        if (cardAmount == 0) {
            redeemGifticon(gifticonPayment, order.getId());
            gifticonPayment.markPaid();
            log.info("[Payment] prepare+확정(전액 기프티콘) orderId={} paymentId={} gifticon={}",
                    order.getId(), gifticonPayment.getId(), gifticonAmount);
            return new PaymentPrepareRes(merchantUid, gifticonAmount, cardAmount,
                    PaymentStatus.PAID, gifticonPayment.getId());
        }

        log.info("[Payment] prepare orderId={} merchantUid={} gifticon={} card={}",
                order.getId(), merchantUid, gifticonAmount, cardAmount);
        return new PaymentPrepareRes(merchantUid, gifticonAmount, cardAmount,
                PaymentStatus.READY, null);
    }

    /* =========================================================
     * 2) 결제 검증
     *
     * - 카드 결제는 카카오페이 approve에서 이미 캡처·금액검증됨
     * - verify는 approve가 저장한 aid와 클라가 보낸 paymentToken 일치만 대조
     * - 일치하면 PAID, 불일치하면 FAILED
     * - GIFTICON row가 있으면 함께 PAID 처리
     * ========================================================= */
    @Transactional
    public PaymentVerifyRes verify(Long userId, PaymentVerifyReq req) {
        Payment cardPayment = paymentRepository.findByMerchantUid(req.merchantUid())
                .orElseThrow(() -> new BaseException(BaseResponseStatus.PAYMENT_NOT_FOUND));

        Order order = orderRepository.findById(cardPayment.getOrderId())
                .orElseThrow(() -> new BaseException(BaseResponseStatus.ORDER_NOT_FOUND));
        if (!order.isPlacedBy(userId)) {
            throw new BaseException(BaseResponseStatus.ACCESS_DENIED);
        }
        if (cardPayment.getStatus() != PaymentStatus.READY) {
            throw new BaseException(BaseResponseStatus.PAYMENT_VERIFICATION_FAILED,
                    "이미 처리된 결제입니다.");
        }
        // verify는 카드 결제 확정 전용. 전액 기프티콘은 prepare에서 이미 확정된다.
        if (cardPayment.getMethod() != PaymentMethod.CARD) {
            throw new BaseException(BaseResponseStatus.PAYMENT_VERIFICATION_FAILED,
                    "카드 결제만 verify로 확정합니다.");
        }

        // 카드 결제는 카카오페이로만 승인된다. ready/approve를 거치지 않은 결제는 확정 불가.
        if (!cardPayment.isKakaoPay()) {
            cardPayment.markFailed();
            log.warn("[Payment] verify FAIL paymentId={} 카카오페이로 준비되지 않은 결제", cardPayment.getId());
            throw new BaseException(BaseResponseStatus.PAYMENT_VERIFICATION_FAILED,
                    "카카오페이로 준비되지 않은 결제입니다.");
        }
        // 카카오페이는 approve 단계에서 이미 결제가 캡처·금액검증됨.
        // verify는 넘어온 paymentToken(=aid)이 approve 때 저장한 aid와 일치하는지만 대조한다.
        if (!req.impUid().equals(cardPayment.getKakaopayAid())) {
            cardPayment.markFailed();
            log.warn("[Payment] verify FAIL(kakaopay) paymentId={} token mismatch", cardPayment.getId());
            throw new BaseException(BaseResponseStatus.PAYMENT_VERIFICATION_FAILED);
        }
        cardPayment.markPaid();

        // 같은 주문의 GIFTICON 결제분도 잔액 차감 후 PAID 처리
        paymentRepository.findAllByOrderId(order.getId()).stream()
                .filter(p -> p.getMethod() == PaymentMethod.GIFTICON
                        && p.getStatus() == PaymentStatus.READY)
                .forEach(p -> {
                    redeemGifticon(p, order.getId());
                    p.markPaid();
                });

        log.info("[Payment] verify OK paymentId={} order={}", cardPayment.getId(), order.getId());
        return new PaymentVerifyRes(cardPayment.getId(), cardPayment.getStatus());
    }

    /**
     * GIFTICON 결제분의 실제 잔액 차감 — 비관적 락·사용내역 기록은 GifticonService.use에 위임.
     * prepare~verify 사이 잔액이 소진됐다면 use 내부 검증에서 예외가 던져진다.
     */
    private void redeemGifticon(Payment gifticonPayment, Long orderId) {
        gifticonService.use(
                gifticonPayment.getGifticonId(),
                new GifticonUseReq(orderId, gifticonPayment.getAmount()));
    }

    /* =========================================================
     * 2-1) 카카오페이 ready (서버 → 카카오페이 payment/ready)
     *
     * 앱은 prepare로 발급된 merchantUid + 카드 결제분 amount를 보낸다.
     * 카카오페이 tid를 결제 row에 저장하고, 앱이 열 redirectUrl을 반환한다.
     * ========================================================= */
    @Transactional
    public KakaoPayReadyRes kakaoPayReady(Long userId, KakaoPayReadyReq req) {
        Payment cardPayment = findReadyCardPayment(userId, req.merchantUid());

        // 앱이 보낸 금액이 사전 준비된 카드 결제분과 일치해야 함 (위조 방어)
        if (!cardPayment.getAmount().equals(req.amount())) {
            throw new BaseException(BaseResponseStatus.PAYMENT_AMOUNT_MISMATCH);
        }

        KakaoPayClient.ReadyResult result = kakaoPayClient.ready(
                req.merchantUid(), String.valueOf(userId), "카페민수 주문", req.amount());

        cardPayment.assignKakaoPayTid(result.tid());
        log.info("[KakaoPay] ready merchantUid={} tid={}", req.merchantUid(), result.tid());
        return new KakaoPayReadyRes(result.tid(), result.redirectUrl());
    }

    /* =========================================================
     * 2-2) 카카오페이 approve (서버 → 카카오페이 payment/approve)
     *
     * pgToken으로 결제를 승인하고 aid를 저장한다.
     * 낙관 금지: 여기서 PAID로 확정하지 않고, 기존 verify가 paymentToken(=aid)으로 최종 확정한다.
     * ========================================================= */
    @Transactional
    public KakaoPayApproveRes kakaoPayApprove(Long userId, KakaoPayApproveReq req) {
        Payment cardPayment = findReadyCardPayment(userId, req.merchantUid());

        // ready에서 저장한 tid와 앱이 보낸 tid 대조
        if (!cardPayment.isKakaoPay() || !cardPayment.getKakaopayTid().equals(req.tid())) {
            throw new BaseException(BaseResponseStatus.PAYMENT_VERIFICATION_FAILED, "유효하지 않은 결제 요청입니다.");
        }

        KakaoPayClient.ApproveResult result = kakaoPayClient.approve(
                req.tid(), req.merchantUid(), String.valueOf(userId), req.pgToken(),
                cardPayment.getAmount());

        // 승인 금액이 주문 금액과 다르면 거부
        if (result.amount() != cardPayment.getAmount()) {
            cardPayment.markFailed();
            log.warn("[KakaoPay] approve amount mismatch paymentId={} approved={} expected={}",
                    cardPayment.getId(), result.amount(), cardPayment.getAmount());
            throw new BaseException(BaseResponseStatus.PAYMENT_AMOUNT_MISMATCH);
        }

        cardPayment.assignKakaoPayAid(result.aid());
        log.info("[KakaoPay] approve OK merchantUid={} aid={}", req.merchantUid(), result.aid());
        return new KakaoPayApproveRes(result.aid());
    }

    /** merchantUid로 READY 상태의 CARD 결제를 찾고 본인 주문인지 검증. */
    private Payment findReadyCardPayment(Long userId, String merchantUid) {
        Payment cardPayment = paymentRepository.findByMerchantUid(merchantUid)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.PAYMENT_NOT_FOUND));
        Order order = orderRepository.findById(cardPayment.getOrderId())
                .orElseThrow(() -> new BaseException(BaseResponseStatus.ORDER_NOT_FOUND));
        if (!order.isPlacedBy(userId)) {
            throw new BaseException(BaseResponseStatus.ACCESS_DENIED);
        }
        if (cardPayment.getStatus() != PaymentStatus.READY) {
            throw new BaseException(BaseResponseStatus.PAYMENT_VERIFICATION_FAILED, "이미 처리된 결제입니다.");
        }
        return cardPayment;
    }

    /* =========================================================
     * 3) 결제 상세
     * ========================================================= */
    public PaymentDetailRes getDetail(Long userId, Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.PAYMENT_NOT_FOUND));
        Order order = orderRepository.findById(payment.getOrderId())
                .orElseThrow(() -> new BaseException(BaseResponseStatus.ORDER_NOT_FOUND));

        boolean isCustomer = order.isPlacedBy(userId);
        boolean isOwner = isStoreOwner(order.getStoreId(), userId);
        if (!isCustomer && !isOwner) {
            throw new BaseException(BaseResponseStatus.ACCESS_DENIED);
        }
        return PaymentDetailRes.from(payment);
    }

    /* =========================================================
     * 4) 매장 결제 내역 (점주, 정산용)
     * ========================================================= */
    public StorePaymentsRes getStorePayments(Long userId, Long storeId,
                                             LocalDate from, LocalDate to) {
        verifyStoreOwner(storeId, userId);

        LocalDateTime fromAt = from != null ? from.atStartOfDay() : null;
        LocalDateTime toAt   = to   != null ? to.plusDays(1).atStartOfDay() : null;

        List<Payment> payments = paymentRepository.findStorePayments(
                storeId, PaymentStatus.PAID, fromAt, toAt);
        return StorePaymentsRes.of(payments);
    }

    /* =========================================================
     * 5) 매장 매출 요약 (점주 대시보드)
     *
     * 모두 PAID 결제(paidAt 기준)만 집계.
     *  - totalSales / dailySales : PAID 결제 목록을 재사용해 Java에서 집계 (DB 방언 의존 X)
     *  - topMenus               : PAID 주문의 OrderItem을 DB에서 집계 (DISTINCT 주문으로 분할결제 중복 방지)
     * ========================================================= */
    public SalesSummaryRes getSalesSummary(Long userId, Long storeId,
                                           LocalDate from, LocalDate to) {
        verifyStoreOwner(storeId, userId);

        LocalDateTime fromAt = from != null ? from.atStartOfDay() : null;
        LocalDateTime toAt   = to   != null ? to.plusDays(1).atStartOfDay() : null;

        List<Payment> payments = paymentRepository.findStorePayments(
                storeId, PaymentStatus.PAID, fromAt, toAt);

        long totalSales = payments.stream().mapToLong(Payment::getAmount).sum();
        List<SalesSummaryRes.DailySales> dailySales = aggregateDaily(payments);

        List<SalesSummaryRes.TopMenu> topMenus = orderItemRepository.findTopMenus(
                        storeId, PaymentStatus.PAID, fromAt, toAt,
                        PageRequest.of(0, TOP_MENU_LIMIT))
                .stream()
                .map(r -> new SalesSummaryRes.TopMenu(
                        r.getMenuId(), r.getName(), r.getQuantity(), r.getAmount()))
                .toList();

        return new SalesSummaryRes(totalSales, dailySales, topMenus);
    }

    /** PAID 결제 목록 → 일자별 버킷(날짜 오름차순). amount=금액 합, orderCount=주문 수(분할결제 중복 제외). */
    private List<SalesSummaryRes.DailySales> aggregateDaily(List<Payment> payments) {
        Map<LocalDate, long[]> amountByDate = new LinkedHashMap<>();           // [금액 합]
        Map<LocalDate, Set<Long>> ordersByDate = new LinkedHashMap<>();        // 분할결제 중복 제거용 주문 ID 집합

        for (Payment p : payments) {
            if (p.getPaidAt() == null) continue;   // 방어 — PAID인데 paidAt null이면 스킵
            LocalDate date = p.getPaidAt().toLocalDate();
            amountByDate.computeIfAbsent(date, d -> new long[1])[0] += p.getAmount();
            ordersByDate.computeIfAbsent(date, d -> new HashSet<>()).add(p.getOrderId());
        }

        return amountByDate.entrySet().stream()
                .map(e -> new SalesSummaryRes.DailySales(
                        e.getKey(),
                        e.getValue()[0],
                        ordersByDate.get(e.getKey()).size()))
                .sorted(Comparator.comparing(SalesSummaryRes.DailySales::date))
                .toList();
    }

    /* ============================
     * helpers
     * ============================ */
    private String generateMerchantUid(Long orderId) {
        return "order_%d_%s".formatted(orderId, UUID.randomUUID().toString().substring(0, 8));
    }

    private boolean isStoreOwner(Long storeId, Long userId) {
        return storeRepository.findById(storeId)
                .map(s -> s.isOwnedBy(userId)).orElse(false);
    }

    private void verifyStoreOwner(Long storeId, Long userId) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.STORE_NOT_FOUND));
        if (!store.isOwnedBy(userId)) {
            throw new BaseException(BaseResponseStatus.NOT_STORE_OWNER);
        }
    }
}
