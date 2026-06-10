package com.cafeminsu.domain.gifticon.service;

import com.cafeminsu.domain.gifticon.dto.GifticonDetailRes;
import com.cafeminsu.domain.gifticon.dto.GifticonPurchaseReq;
import com.cafeminsu.domain.gifticon.dto.GifticonPurchaseRes;
import com.cafeminsu.domain.gifticon.dto.GifticonShareRes;
import com.cafeminsu.domain.gifticon.dto.GifticonUsageRes;
import com.cafeminsu.domain.gifticon.dto.GifticonUseReq;
import com.cafeminsu.domain.gifticon.dto.GifticonUseRes;
import com.cafeminsu.domain.gifticon.dto.GifticonValidateRes;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GifticonService {

    /** 기본 유효기간 6개월 */
    private static final int DEFAULT_VALIDITY_MONTHS = 6;

    private final GifticonRepository gifticonRepository;
    private final GifticonUsageRepository usageRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final StoreRepository storeRepository;

    /* =========================================================
     * 1) 기프티콘 구매·발행
     * ========================================================= */
    @Transactional
    public GifticonPurchaseRes purchase(Long userId, GifticonPurchaseReq req) {
        // receiverId 또는 receiverPhone 중 하나는 필수
        if (req.receiverId() == null && !StringUtils.hasText(req.receiverPhone())) {
            throw new BaseException(BaseResponseStatus.INVALID_REQUEST,
                    "receiverId 또는 receiverPhone 중 하나는 필수입니다.");
        }
        // receiverId 검증
        if (req.receiverId() != null && !userRepository.existsById(req.receiverId())) {
            throw new BaseException(BaseResponseStatus.USER_NOT_FOUND);
        }

        String qrCode = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusMonths(DEFAULT_VALIDITY_MONTHS);

        Gifticon gifticon = Gifticon.builder()
                .senderId(userId)
                .receiverId(req.receiverId())
                .receiverPhone(req.receiverPhone())
                .amount(req.amount())
                .qrCode(qrCode)
                .message(req.message())
                .expiresAt(expiresAt)
                .build();
        Gifticon saved = gifticonRepository.save(gifticon);

        // TODO: 포트원 결제 prepare 호출 → merchantUid 발급. 현재는 mock UID.
        String merchantUid = "gift_%d_%s".formatted(saved.getId(),
                UUID.randomUUID().toString().substring(0, 8));

        log.info("[Gifticon] purchased id={} sender={} amount={}",
                saved.getId(), userId, req.amount());
        return new GifticonPurchaseRes(saved.getId(), saved.getQrCode(), merchantUid);
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
        return GifticonDetailRes.from(g);
    }

    /* =========================================================
     * 6) QR 스캔 검증
     * ========================================================= */
    public GifticonValidateRes validate(String qrCode) {
        Gifticon g = gifticonRepository.findByQrCode(qrCode)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.GIFTICON_INVALID_QR));

        boolean valid = g.isUsable();
        String ownerNickname = null;
        if (g.getReceiverId() != null) {
            ownerNickname = userRepository.findById(g.getReceiverId())
                    .map(User::getNickname).orElse(null);
        }
        return new GifticonValidateRes(g.getId(), g.getBalance(), ownerNickname, valid);
    }

    /* =========================================================
     * 7) 기프티콘 사용 (차감) — 비관적 락
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
     * 8) 공유 링크 발급 (선물하기)
     * ========================================================= */
    public GifticonShareRes share(Long userId, Long gifticonId) {
        Gifticon g = findOrThrow(gifticonId);
        if (!g.isSentBy(userId)) {
            throw new BaseException(BaseResponseStatus.ACCESS_DENIED);
        }
        // TODO: 카카오 메시지 API 연동. 현재는 단순 링크.
        String shareLink = "https://cafeminsu.example/gifticon/" + g.getQrCode();
        String deepLink = "cafeminsu://gifticon/" + g.getQrCode();
        return new GifticonShareRes(shareLink, deepLink);
    }

    /* =========================================================
     * 9) 사용 내역
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
