package com.cafeminsu.domain.gifticon.service;

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
import com.cafeminsu.domain.gifticon.entity.Gifticon;
import com.cafeminsu.domain.gifticon.entity.GifticonStatus;
import com.cafeminsu.domain.gifticon.entity.GifticonUsage;
import com.cafeminsu.domain.gifticon.repository.GifticonRepository;
import com.cafeminsu.domain.gifticon.repository.GifticonUsageRepository;
import com.cafeminsu.domain.order.entity.Order;
import com.cafeminsu.domain.order.repository.OrderRepository;
import com.cafeminsu.domain.store.repository.StoreRepository;
import com.cafeminsu.domain.user.entity.User;
import com.cafeminsu.domain.user.repository.UserRepository;
import com.cafeminsu.global.common.BaseResponseStatus;
import com.cafeminsu.global.exception.BaseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GifticonService {

    /** 기본 유효기간 6개월 */
    private static final int DEFAULT_VALIDITY_MONTHS = 6;
    /** 스탬프 10개 적립 시 지급되는 보상 금액(원) */
    private static final int STAMP_REWARD_AMOUNT = 2000;

    /** 클레임 코드 문자 집합 — 혼동되는 0/O/1/I/L 제외. */
    private static final char[] CLAIM_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final GifticonRepository gifticonRepository;
    private final GifticonUsageRepository usageRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final StoreRepository storeRepository;

    /** 공유 링크 베이스 URL. 실제 값은 application-{profile}.yml/환경변수로 주입. */
    @Value("${gifticon.share-base-url:https://cafeminsu.example/gift}")
    private String shareBaseUrl;

    /* =========================================================
     * 1) 기프티콘 구매·발행
     *
     * 친구 선물(링크 방식): 수신자 미지정으로 발행하고 claimCode/shareLink를 반환한다.
     * 구매자가 그 링크를 카카오톡 메시지/공유로 전달하면 받는 사람이 claim(등록)으로 귀속한다.
     * receiverId(회원 즉시 지정)/receiverPhone(비회원)은 하위호환으로 선택 허용.
     * ========================================================= */
    @Transactional
    public GifticonPurchaseRes purchase(Long userId, GifticonPurchaseReq req) {
        // receiverId가 주어진 경우에만 존재 검증 (미지정이면 link 방식)
        if (req.receiverId() != null && !userRepository.existsById(req.receiverId())) {
            throw new BaseException(BaseResponseStatus.USER_NOT_FOUND);
        }

        String claimCode = generateUniqueClaimCode();
        LocalDateTime expiresAt = LocalDateTime.now().plusMonths(DEFAULT_VALIDITY_MONTHS);

        Gifticon gifticon = Gifticon.builder()
                .senderId(userId)
                .receiverId(req.receiverId())       // null이면 미귀속(받는 사람이 claim)
                .receiverPhone(req.receiverPhone())
                .amount(req.amount())
                .claimToken(claimCode)
                .message(req.message())
                .expiresAt(expiresAt)
                .build();
        Gifticon saved = gifticonRepository.save(gifticon);

        // TODO: 결제 prepare 연동 (현재 MVP는 결제 검증 생략, 즉시 발행)
        log.info("[Gifticon] purchased id={} sender={} amount={}",
                saved.getId(), userId, req.amount());
        return new GifticonPurchaseRes(
                saved.getId(), claimCode, buildShareLink(claimCode), saved.getAmount(), saved.getMessage());
    }

    /* =========================================================
     * 1-2) 기프티콘 등록 (claim) — 받는 사람이 코드로 자기 계정에 귀속
     *
     * - 유효 코드 → 호출자(JWT) 계정에 귀속, 이후 /my 에 노출
     * - 본인이 이미 등록함 → 멱등 성공
     * - 타인이 이미 등록함 → ALREADY_CLAIMED(409)
     * - 만료/없음 → 4xx
     * - 발신자 본인이 자기 코드 등록도 허용(본인 보관용)
     * ========================================================= */
    @Transactional
    public GifticonClaimRes claim(Long userId, String claimCode) {
        // 동시 등록 직렬화: 같은 링크를 받은 두 사람이 동시에 눌러도 한쪽만 귀속.
        Gifticon g = gifticonRepository.findByClaimTokenForUpdate(claimCode)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.GIFTICON_INVALID_CODE));

        if (g.isExpired()) {
            throw new BaseException(BaseResponseStatus.GIFTICON_EXPIRED);
        }
        if (g.isClaimed()) {
            if (g.isReceivedBy(userId)) {
                return GifticonClaimRes.from(g);   // 멱등 성공
            }
            throw new BaseException(BaseResponseStatus.GIFTICON_ALREADY_CLAIMED);
        }

        g.claimBy(userId);
        log.info("[Gifticon] claimed id={} receiver={}", g.getId(), userId);
        return GifticonClaimRes.from(g);
    }

    /* =========================================================
     * 1-1) 스탬프 적립 보상 기프티콘 발급 (StampService에서 호출)
     *
     * 본인 전용(sender=receiver=본인) — 발급 즉시 본인 귀속이므로 타인은 claim 불가.
     * 전 매장 공용 금액형.
     * ========================================================= */
    @Transactional
    public Long issueStampReward(Long userId) {
        String claimCode = generateUniqueClaimCode();
        LocalDateTime expiresAt = LocalDateTime.now().plusMonths(DEFAULT_VALIDITY_MONTHS);

        Gifticon reward = Gifticon.builder()
                .senderId(userId)
                .receiverId(userId)   // 본인 전용 (이미 귀속됨)
                .amount(STAMP_REWARD_AMOUNT)
                .claimToken(claimCode)
                .message("스탬프 적립 보상")
                .expiresAt(expiresAt)
                .build();
        Gifticon saved = gifticonRepository.save(reward);

        log.info("[Gifticon] stamp reward issued id={} user={} amount={}",
                saved.getId(), userId, STAMP_REWARD_AMOUNT);
        return saved.getId();
    }

    /* =========================================================
     * 2) 보낸 기프티콘 목록
     * ========================================================= */
    public List<SentGifticonRes> getSent(Long userId) {
        List<Gifticon> sent = gifticonRepository.findAllBySenderIdOrderByIdDesc(userId);
        Map<Long, String> nicknames = nicknameMap(
                sent.stream().map(Gifticon::getReceiverId).filter(java.util.Objects::nonNull).toList()
        );
        return sent.stream()
                .map(g -> SentGifticonRes.of(g, nicknameOrPhone(g.getReceiverId(), g.getReceiverPhone(), nicknames)))
                .toList();
    }

    /* =========================================================
     * 3) 받은 기프티콘 목록
     * ========================================================= */
    public List<ReceivedGifticonRes> getReceived(Long userId) {
        List<Gifticon> received = gifticonRepository.findAllByReceiverIdOrderByIdDesc(userId);
        Map<Long, String> nicknames = nicknameMap(received.stream().map(Gifticon::getSenderId).toList());
        return received.stream()
                .map(g -> ReceivedGifticonRes.of(g, nicknames.getOrDefault(g.getSenderId(), "(알 수 없음)")))
                .toList();
    }

    /* =========================================================
     * 4) 내 사용 가능 기프티콘 (결제 화면)
     * ========================================================= */
    public List<MyGifticonRes> getUsable(Long userId) {
        return gifticonRepository.findUsableByReceiverId(userId).stream()
                .map(MyGifticonRes::from)
                .toList();
    }

    /* =========================================================
     * 5) 기프티콘 상세 (본인 sender 또는 receiver만)
     * ========================================================= */
    public GifticonDetailRes getDetail(Long userId, Long gifticonId) {
        Gifticon g = findOrThrow(gifticonId);
        if (!g.isSentBy(userId) && !g.isReceivedBy(userId)) {
            throw new BaseException(BaseResponseStatus.ACCESS_DENIED);
        }
        // claimCode/shareLink는 발신자가 재전송할 수 있게 함께 노출
        return GifticonDetailRes.of(g, buildShareLink(g.getClaimToken()));
    }

    /* =========================================================
     * 5-1) 결제 사용 가능 여부 검증 (결제 prepare 단계에서 호출)
     *
     * 차감은 하지 않고 소유·만료·잔액만 확인한다(빠른 실패용).
     * 실제 차감 시점(use)에 비관적 락으로 다시 안전하게 검증된다.
     * ========================================================= */
    public void assertUsableForPayment(Long userId, Long gifticonId, int amount) {
        Gifticon g = findOrThrow(gifticonId);
        if (!g.isReceivedBy(userId)) {
            throw new BaseException(BaseResponseStatus.ACCESS_DENIED);
        }
        if (g.isExpired()) {
            throw new BaseException(BaseResponseStatus.GIFTICON_EXPIRED);
        }
        if (!g.isUsable()) {
            throw new BaseException(BaseResponseStatus.GIFTICON_ALREADY_USED);
        }
        if (amount > g.getBalance()) {
            throw new BaseException(BaseResponseStatus.GIFTICON_INSUFFICIENT_BALANCE);
        }
    }

    /* =========================================================
     * 6) 기프티콘 사용 (차감) — 비관적 락
     * ========================================================= */
    @Transactional
    public GifticonUseRes use(Long gifticonId, GifticonUseReq req) {
        // SELECT ... FOR UPDATE로 row 락. 같은 기프티콘 동시 차감 방지.
        Gifticon g = gifticonRepository.findByIdForUpdate(gifticonId)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.GIFTICON_NOT_FOUND));

        // 주문 존재 확인
        Order order = orderRepository.findById(req.orderId())
                .orElseThrow(() -> new BaseException(BaseResponseStatus.ORDER_NOT_FOUND));

        g.use(req.usedAmount());  // 엔티티가 상태·만료·잔액 검증 수행

        usageRepository.save(GifticonUsage.builder()
                .gifticonId(g.getId())
                .orderId(order.getId())
                .usedAmount(req.usedAmount())
                .balanceAfter(g.getBalance())
                .build());

        log.info("[Gifticon] used id={} amount={} balanceAfter={}",
                g.getId(), req.usedAmount(), g.getBalance());
        return new GifticonUseRes(g.getBalance(), g.getStatus());
    }

    /* =========================================================
     * 7) 사용 내역
     * ========================================================= */
    public List<GifticonUsageRes> getUsages(Long userId, Long gifticonId) {
        Gifticon g = findOrThrow(gifticonId);
        if (!g.isSentBy(userId) && !g.isReceivedBy(userId)) {
            throw new BaseException(BaseResponseStatus.ACCESS_DENIED);
        }
        List<GifticonUsage> usages = usageRepository.findAllByGifticonIdOrderByIdDesc(gifticonId);

        // 사용된 매장명 — order_id로 store 찾기
        Set<Long> orderIds = usages.stream().map(GifticonUsage::getOrderId).collect(Collectors.toSet());
        Map<Long, Long> orderToStore = orderIds.isEmpty() ? Map.of()
                : orderRepository.findAllById(orderIds).stream()
                .collect(Collectors.toMap(Order::getId, Order::getStoreId));
        Set<Long> storeIds = new HashSet<>(orderToStore.values());
        Map<Long, String> storeNames = storeIds.isEmpty() ? Map.of()
                : storeRepository.findAllById(storeIds).stream()
                .collect(Collectors.toMap(s -> s.getId(), s -> s.getName()));

        return usages.stream()
                .map(u -> {
                    Long storeId = orderToStore.get(u.getOrderId());
                    String storeName = storeId != null ? storeNames.getOrDefault(storeId, "(삭제된 매장)") : "(미지정)";
                    return GifticonUsageRes.of(u, storeName);
                })
                .toList();
    }

    /* ============================
     * helpers
     * ============================ */
    private Gifticon findOrThrow(Long id) {
        return gifticonRepository.findById(id)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.GIFTICON_NOT_FOUND));
    }

    /** GFT-XXXX-XXXX 형식의 추측 불가 코드 발급. UNIQUE 충돌 시 재시도. */
    private String generateUniqueClaimCode() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String code = "GFT-" + randomBlock(4) + "-" + randomBlock(4);
            if (!gifticonRepository.existsByClaimToken(code)) {
                return code;
            }
        }
        // 사실상 도달 불가 — 5회 연속 충돌이면 UUID로 폴백
        return "GFT-" + UUID.randomUUID().toString().substring(0, 13).toUpperCase().replace("-", "");
    }

    private String randomBlock(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(CLAIM_ALPHABET[RANDOM.nextInt(CLAIM_ALPHABET.length)]);
        }
        return sb.toString();
    }

    /** 공유 링크 — 받는 사람이 눌러 클레임 페이지/딥링크로 진입하고 code로 등록. */
    private String buildShareLink(String claimCode) {
        return shareBaseUrl + "?code=" + claimCode;
    }

    private Map<Long, String> nicknameMap(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return Map.of();
        return userRepository.findAllById(new HashSet<>(userIds)).stream()
                .filter(u -> u.getNickname() != null)
                .collect(Collectors.toMap(User::getId, User::getNickname, (a, b) -> a));
    }

    private String nicknameOrPhone(Long receiverId, String phone, Map<Long, String> nicknames) {
        if (receiverId != null && nicknames.containsKey(receiverId)) return nicknames.get(receiverId);
        if (StringUtils.hasText(phone)) return maskPhone(phone);
        return "(미지정)";
    }

    /** 전화번호 마스킹 — 010-1234-5678 → 010-****-5678 */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) return phone;
        int len = phone.length();
        return phone.substring(0, Math.min(3, len)) + "****" + phone.substring(Math.max(len - 4, 0));
    }
}
