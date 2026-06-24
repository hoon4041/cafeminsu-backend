package com.cafeminsu.domain.gifticon.entity;

import com.cafeminsu.global.common.BaseEntity;
import com.cafeminsu.global.common.BaseResponseStatus;
import com.cafeminsu.global.exception.BaseException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "gifticons",
        indexes = {
                @Index(name = "idx_gift_sender", columnList = "sender_id"),
                @Index(name = "idx_gift_receiver", columnList = "receiver_id"),
                @Index(name = "idx_gift_claim_token", columnList = "claim_token", unique = true),
                @Index(name = "idx_gift_status", columnList = "status")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Gifticon extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    /**
     * 수신자 user_id.
     * 구매 시점에는 미지정(null)일 수 있고, 수신자가 claim(등록)하면 그때 채워진다.
     * receiverId != null 이면 '귀속 완료'를 의미한다.
     */
    @Column(name = "receiver_id")
    private Long receiverId;

    /** 수신자 전화번호. receiverId가 채워지면 null로 두거나 함께 보존. */
    @Column(name = "receiver_phone", length = 20)
    private String receiverPhone;

    /** 최초 발행 금액 — 변경되지 않음 (영수증·정산용) */
    @Column(nullable = false)
    private Integer amount;

    /** 현재 잔액 — 차감될 때마다 감소 */
    @Column(nullable = false)
    private Integer balance;

    /**
     * 클레임 코드 — 추측 불가능한 1회성 토큰(예: GFT-XXXX-XXXX).
     * 발신자가 공유 링크로 전달하면 수신자가 이 코드로 등록(claim)한다.
     * 베어러 토큰처럼 동작하므로 로그에 남기지 않는다.
     */
    @Column(name = "claim_token", nullable = false, length = 100, unique = true)
    private String claimToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GifticonStatus status;

    @Column(length = 200)
    private String message;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Builder
    private Gifticon(Long senderId, Long receiverId, String receiverPhone,
                     Integer amount, String claimToken, String message,
                     LocalDateTime expiresAt) {
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.receiverPhone = receiverPhone;
        this.amount = amount;
        this.balance = amount;  // 발행 시 잔액 = 금액
        this.claimToken = claimToken;
        this.message = message;
        this.expiresAt = expiresAt;
        this.status = GifticonStatus.UNUSED;
    }

    /** 이미 누군가에게 귀속되었는지 (claim 완료 여부). */
    public boolean isClaimed() {
        return this.receiverId != null;
    }

    /**
     * 수신자에게 귀속 — claim(등록) 시 호출.
     * 미귀속 상태에서만 가능. 이미 귀속된 건은 호출 측에서 멱등/충돌 처리.
     */
    public void claimBy(Long userId) {
        this.receiverId = userId;
    }

    /**
     * 잔액 차감 — 동시성 안전.
     *
     * 호출 측에서 반드시 SELECT ... FOR UPDATE로 row 락을 잡은 상태로 들어와야 함.
     * (Repository.findByIdForUpdate 사용)
     */
    public void use(int usedAmount) {
        if (usedAmount <= 0) {
            throw new BaseException(BaseResponseStatus.INVALID_REQUEST, "usedAmount는 1 이상이어야 합니다.");
        }
        if (isExpired()) {
            this.status = GifticonStatus.EXPIRED;
            throw new BaseException(BaseResponseStatus.GIFTICON_EXPIRED);
        }
        if (this.status == GifticonStatus.USED || this.balance <= 0) {
            throw new BaseException(BaseResponseStatus.GIFTICON_ALREADY_USED);
        }
        if (usedAmount > this.balance) {
            throw new BaseException(BaseResponseStatus.GIFTICON_INSUFFICIENT_BALANCE);
        }
        this.balance -= usedAmount;
        // 상태 갱신
        if (this.balance == 0) {
            this.status = GifticonStatus.USED;
        } else if (this.balance < this.amount) {
            this.status = GifticonStatus.PARTIAL;
        }
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiresAt);
    }

    public boolean isUsable() {
        return !isExpired()
                && this.balance > 0
                && this.status != GifticonStatus.USED
                && this.status != GifticonStatus.EXPIRED;
    }

    /** 수신자에게 속하는지 (회원이면 receiverId, 아니면 receiverPhone 기반) */
    public boolean isReceivedBy(Long userId) {
        return this.receiverId != null && this.receiverId.equals(userId);
    }

    public boolean isSentBy(Long userId) {
        return this.senderId.equals(userId);
    }
}
