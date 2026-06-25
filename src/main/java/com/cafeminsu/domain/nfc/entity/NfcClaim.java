package com.cafeminsu.domain.nfc.entity;

import com.cafeminsu.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * NFC 태깅으로 쿠폰을 발급받은 이력.
 *
 * (tag_id, user_id, claim_date) 유니크 제약으로 "하루 1회" 발급을 DB가 보장한다.
 * 동시에 두 번 태깅(race)해도 한쪽만 통과한다.
 */
@Entity
@Getter
@Table(name = "nfc_claims",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_nfc_claim_tag_user_date",
                columnNames = {"tag_id", "user_id", "claim_date"}),
        indexes = {
                @Index(name = "idx_nfc_claim_user", columnList = "user_id")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NfcClaim extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tag_id", nullable = false)
    private Long tagId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 발급 매장(분석·정산용). 쿠폰 자체는 전 매장 공용 금액형이다. */
    @Column(name = "store_id", nullable = false)
    private Long storeId;

    /** 발급일(날짜 단위). (tag,user,date) 유니크로 '하루 1회'를 보장. */
    @Column(name = "claim_date", nullable = false)
    private LocalDate claimDate;

    /** 발급된 기프티콘 id. */
    @Column(name = "gifticon_id")
    private Long gifticonId;

    @Builder
    private NfcClaim(Long tagId, Long userId, Long storeId, LocalDate claimDate, Long gifticonId) {
        this.tagId = tagId;
        this.userId = userId;
        this.storeId = storeId;
        this.claimDate = claimDate;
        this.gifticonId = gifticonId;
    }

    public void linkGifticon(Long gifticonId) {
        this.gifticonId = gifticonId;
    }
}
