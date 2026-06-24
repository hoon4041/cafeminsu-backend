package com.cafeminsu.domain.payment.entity;

import com.cafeminsu.global.common.BaseEntity;
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

/**
 * 결제. 분할결제(기프티콘+카드)인 경우 한 주문에 row 2개 — method 다름.
 */
@Entity
@Getter
@Table(name = "payments",
        indexes = {
                @Index(name = "idx_pay_order", columnList = "order_id"),
                @Index(name = "idx_pay_status", columnList = "status"),
                @Index(name = "idx_pay_paidat", columnList = "paid_at")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    /** 포트원 imp_uid. CARD 결제만 채워짐. GIFTICON은 null. */
    @Column(name = "portone_imp_uid", length = 100)
    private String portoneImpUid;

    /** 우리 쪽 결제 식별자 (포트원 merchant_uid). CARD 결제분에만 채워짐. */
    @Column(name = "merchant_uid", length = 100, unique = true)
    private String merchantUid;

    @Column(nullable = false)
    private Integer amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    /** 분할결제 시 GIFTICON 결제분의 기프티콘 ID. CARD는 null. */
    @Column(name = "gifticon_id")
    private Long gifticonId;

    /** 카카오페이 ready 응답의 tid. 카카오페이 결제분에만 채워짐(이 값이 있으면 카카오페이 결제). */
    @Column(name = "kakaopay_tid", length = 100)
    private String kakaopayTid;

    /** 카카오페이 approve 응답의 aid(결제 승인 번호). verify가 paymentToken으로 대조. */
    @Column(name = "kakaopay_aid", length = 100)
    private String kakaopayAid;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Builder
    private Payment(Long orderId, String merchantUid, Integer amount,
                    PaymentMethod method, Long gifticonId) {
        this.orderId = orderId;
        this.merchantUid = merchantUid;
        this.amount = amount;
        this.method = method;
        this.gifticonId = gifticonId;
        this.status = PaymentStatus.READY;
    }

    public void markPaid(String impUid) {
        this.portoneImpUid = impUid;
        this.status = PaymentStatus.PAID;
        this.paidAt = LocalDateTime.now();
    }

    /** 카카오페이 ready 성공 시 tid 저장. */
    public void assignKakaoPayTid(String tid) {
        this.kakaopayTid = tid;
    }

    /** 카카오페이 approve 성공 시 승인번호(aid) 저장. */
    public void assignKakaoPayAid(String aid) {
        this.kakaopayAid = aid;
    }

    /** 카카오페이 결제분 여부 — tid가 있으면 카카오페이로 ready된 결제. */
    public boolean isKakaoPay() {
        return this.kakaopayTid != null;
    }

    public void markPaidWithoutImpUid() {
        this.status = PaymentStatus.PAID;
        this.paidAt = LocalDateTime.now();
    }

    public void markFailed() {
        this.status = PaymentStatus.FAILED;
    }

    public void markRefunded() {
        this.status = PaymentStatus.REFUNDED;
    }
}
