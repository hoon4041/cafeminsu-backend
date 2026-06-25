package com.cafeminsu.domain.nfc.entity;

import com.cafeminsu.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 매장에 부착되는 물리 NFC 태그 1장 = row 1개.
 *
 * 손님이 태깅하면 앱이 태그에 기록된 {@link #code}를 읽어 발급 API를 호출한다.
 * code는 하드웨어 UID가 아니라 서버가 발급한 추측 불가 시크릿이며, 베어러 토큰처럼
 * 다뤄야 한다(로그에 남기지 않는다).
 */
@Entity
@Getter
@Table(name = "nfc_tags",
        indexes = {
                @Index(name = "idx_nfc_tag_store", columnList = "store_id"),
                @Index(name = "idx_nfc_tag_code", columnList = "code", unique = true)
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NfcTag extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    /** 태그에 기록되는 추측 불가 시크릿(NFC-XXXX-XXXX). 베어러 취급 — 로그 금지. */
    @Column(nullable = false, length = 100, unique = true)
    private String code;

    /** 태깅 1회당 발급할 쿠폰 금액(원). */
    @Column(name = "reward_amount", nullable = false)
    private Integer rewardAmount;

    /** 발급 쿠폰에 표기할 문구. null이면 기본 문구 사용. */
    @Column(length = 200)
    private String message;

    /** 비활성화하면 더 이상 발급되지 않는다(분실·교체 태그 회수용). */
    @Column(nullable = false)
    private boolean active;

    @Builder
    private NfcTag(Long storeId, String code, Integer rewardAmount, String message) {
        this.storeId = storeId;
        this.code = code;
        this.rewardAmount = rewardAmount;
        this.message = message;
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }
}
