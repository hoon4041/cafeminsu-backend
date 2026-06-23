package com.cafeminsu.domain.payment.service;

import com.cafeminsu.domain.order.entity.Order;
import com.cafeminsu.domain.order.entity.OrderStatus;
import com.cafeminsu.domain.order.repository.OrderItemRepository;
import com.cafeminsu.domain.order.repository.OrderRepository;
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
    private final PortoneClient portoneClient;

    /* =========================================================
     * 1) 결제 준비
     *
     * - 주문 본인 확인
     * - 주문 상태가 PENDING이어야 함 (이미 결제했거나 취소된 주문은 불가)
     * - 금액 합계 검증: gifticonAmount + cardAmount == order.totalAmount
     * - 분할이면 Payment row 2개 생성, 단일이면 1개
     * - merchantUid 발급해서 응답 (포트원 결제창 호출용)
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

        int gifticonAmount = req.gifticonAmount() != null ? req.gifticonAmount() : 0;
        int cardAmount = req.cardAmount() != null ? req.cardAmount() : 0;

        // 분할결제 금액 검증
        if (gifticonAmount + cardAmount != order.getTotalAmount()) {
            throw new BaseException(BaseResponseStatus.SPLIT_PAYMENT_AMOUNT_INVALID);
        }
        // 기프티콘 사용 시 ID 필수
        if (gifticonAmount > 0 && req.useGifticonId() == null) {
            throw new BaseException(BaseResponseStatus.INVALID_REQUEST,
                    "gifticonAmount가 있을 때 useGifticonId는 필수입니다.");
        }
        // TODO: 기프티콘 잔액 검증 (Gifticon 도메인 구현 후) — 현재는 ID만 신뢰

        String merchantUid = generateMerchantUid(order.getId());

        // 기프티콘 결제분
        if (gifticonAmount > 0) {
            paymentRepository.save(Payment.builder()
                    .orderId(order.getId())
                    .amount(gifticonAmount)
                    .method(PaymentMethod.GIFTICON)
                    .gifticonId(req.useGifticonId())
                    .build());
        }
        // 카드 결제분 — merchantUid는 카드 row에만
        if (cardAmount > 0) {
            paymentRepository.save(Payment.builder()
                    .orderId(order.getId())
                    .merchantUid(merchantUid)
                    .amount(cardAmount)
                    .method(PaymentMethod.CARD)
                    .build());
        } else {
            // 전액 기프티콘 — 카드 row 없지만 verify가 통과 가능하도록
            // merchantUid를 GIFTICON row의 식별자로도 쓸 수 있게... 일단 별도 처리는 안 하고
            // verify에서 merchantUid로 찾을 수 있게 GIFTICON row에 merchantUid 박아두자.
            // (단, 전액 기프티콘은 사실상 verify 단계가 필요 없지만 API 일관성 위해 허용)
        }

        log.info("[Payment] prepare orderId={} merchantUid={} gifticon={} card={}",
                order.getId(), merchantUid, gifticonAmount, cardAmount);
        return new PaymentPrepareRes(merchantUid, cardAmount);
    }

    /* =========================================================
     * 2) 결제 검증
     *
     * - 포트원에 imp_uid로 결제 조회
     * - 우리 DB amount와 일치 확인 (금액 위조 방어)
     * - 일치하면 PAID, 불일치하면 FAILED (운영에선 환불도)
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

        // 포트원에서 실제 결제 금액 조회
        PortoneClient.VerificationResult result = portoneClient.verify(
                req.impUid(), cardPayment.getAmount());

        if (!result.isPaid() || result.amount() != cardPayment.getAmount()) {
            cardPayment.markFailed();
            log.warn("[Payment] verify FAIL paymentId={} portoneStatus={} portoneAmount={}",
                    cardPayment.getId(), result.status(), result.amount());
            throw new BaseException(BaseResponseStatus.PAYMENT_AMOUNT_MISMATCH);
        }

        cardPayment.markPaid(req.impUid());

        // 같은 주문의 GIFTICON 결제분도 함께 PAID 처리
        paymentRepository.findAllByOrderId(order.getId()).stream()
                .filter(p -> p.getMethod() == PaymentMethod.GIFTICON
                        && p.getStatus() == PaymentStatus.READY)
                .forEach(p -> {
                    // TODO: 기프티콘 잔액 차감 (Gifticon 도메인 구현 후)
                    p.markPaidWithoutImpUid();
                });

        log.info("[Payment] verify OK paymentId={} order={}", cardPayment.getId(), order.getId());
        return new PaymentVerifyRes(cardPayment.getId(), cardPayment.getStatus());
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
