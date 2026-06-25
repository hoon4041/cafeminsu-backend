package com.cafeminsu.domain.nfc.service;

import com.cafeminsu.domain.gifticon.entity.Gifticon;
import com.cafeminsu.domain.gifticon.service.GifticonService;
import com.cafeminsu.domain.nfc.dto.NfcClaimRes;
import com.cafeminsu.domain.nfc.dto.NfcTagCreateReq;
import com.cafeminsu.domain.nfc.dto.NfcTagCreateRes;
import com.cafeminsu.domain.nfc.entity.NfcClaim;
import com.cafeminsu.domain.nfc.entity.NfcTag;
import com.cafeminsu.domain.nfc.repository.NfcClaimRepository;
import com.cafeminsu.domain.nfc.repository.NfcTagRepository;
import com.cafeminsu.domain.store.entity.Store;
import com.cafeminsu.domain.store.repository.StoreRepository;
import com.cafeminsu.global.common.BaseResponseStatus;
import com.cafeminsu.global.exception.BaseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NfcService {

    /** 코드 문자 집합 — 혼동되는 0/O/1/I/L 제외. */
    private static final char[] CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final NfcTagRepository tagRepository;
    private final NfcClaimRepository claimRepository;
    private final StoreRepository storeRepository;
    private final GifticonService gifticonService;

    /** 태그 URL 베이스. 실제 값은 application-{profile}.yml/환경변수로 주입. */
    @Value("${nfc.tag-base-url:https://cafeminsu.example/nfc}")
    private String tagBaseUrl;

    /* =========================================================
     * 1) 태그 등록 (점주 전용)
     *
     * 서버가 추측 불가 시크릿 코드를 발급해 태그에 기록하게 한다.
     * code는 등록 직후 한 번만 노출(응답)되고 이후 조회로는 내려주지 않는다.
     * ========================================================= */
    @Transactional
    public NfcTagCreateRes createTag(Long userId, NfcTagCreateReq req) {
        Store store = storeRepository.findById(req.storeId())
                .orElseThrow(() -> new BaseException(BaseResponseStatus.STORE_NOT_FOUND));
        if (!store.isOwnedBy(userId)) {
            throw new BaseException(BaseResponseStatus.NOT_STORE_OWNER);
        }

        String code = generateUniqueCode();
        NfcTag tag = tagRepository.save(NfcTag.builder()
                .storeId(store.getId())
                .code(code)
                .rewardAmount(req.rewardAmount())
                .message(req.message())
                .build());

        log.info("[Nfc] tag created id={} store={} amount={}",
                tag.getId(), store.getId(), req.rewardAmount());
        return new NfcTagCreateRes(tag.getId(), code, buildTagUrl(code));
    }

    /* =========================================================
     * 2) 태깅 → 쿠폰 발급 (손님)
     *
     * 하루 1회 — (tag,user,date) 유니크 제약으로 보장한다.
     * 쿠폰(기프티콘)을 먼저 발급하면 중복 시 쿠폰만 새고 claim은 실패하므로,
     * claim row를 같은 트랜잭션에서 먼저 확정(flush)한 뒤 쿠폰을 발급한다.
     * (중복이면 flush에서 예외 → 트랜잭션 롤백 → 쿠폰도 발급되지 않음)
     * ========================================================= */
    @Transactional
    public NfcClaimRes claim(Long userId, String tagCode) {
        NfcTag tag = tagRepository.findByCode(tagCode)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.NFC_TAG_NOT_FOUND));
        if (!tag.isActive()) {
            throw new BaseException(BaseResponseStatus.NFC_TAG_INACTIVE);
        }

        LocalDate today = LocalDate.now();
        // 빠른 경로 — 이미 오늘 받았으면 바로 거절(롤백 회피).
        if (claimRepository.existsByTagIdAndUserIdAndClaimDate(tag.getId(), userId, today)) {
            throw new BaseException(BaseResponseStatus.NFC_CLAIM_COOLDOWN);
        }

        NfcClaim claim = NfcClaim.builder()
                .tagId(tag.getId())
                .userId(userId)
                .storeId(tag.getStoreId())
                .claimDate(today)
                .build();
        try {
            // 동시 따닥(race) 방어 — 유니크 제약 위반은 여기서 터진다.
            claimRepository.saveAndFlush(claim);
        } catch (DataIntegrityViolationException e) {
            throw new BaseException(BaseResponseStatus.NFC_CLAIM_COOLDOWN);
        }

        Gifticon coupon = gifticonService.issueNfcReward(userId, tag.getRewardAmount(), tag.getMessage());
        claim.linkGifticon(coupon.getId());

        log.info("[Nfc] claimed tag={} user={} gifticon={}", tag.getId(), userId, coupon.getId());
        return NfcClaimRes.from(coupon);
    }

    /* ============================
     * helpers
     * ============================ */

    /** NFC-XXXX-XXXX 형식의 추측 불가 코드. UNIQUE 충돌 시 재시도. */
    private String generateUniqueCode() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String code = "NFC-" + randomBlock(4) + "-" + randomBlock(4);
            if (!tagRepository.existsByCode(code)) {
                return code;
            }
        }
        return "NFC-" + UUID.randomUUID().toString().substring(0, 13).toUpperCase().replace("-", "");
    }

    private String randomBlock(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(CODE_ALPHABET[RANDOM.nextInt(CODE_ALPHABET.length)]);
        }
        return sb.toString();
    }

    private String buildTagUrl(String code) {
        return tagBaseUrl + "?code=" + code;
    }
}
