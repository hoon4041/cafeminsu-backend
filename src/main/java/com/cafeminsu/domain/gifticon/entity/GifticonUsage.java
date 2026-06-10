package com.cafeminsu.domain.gifticon.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 기프티콘 사용 이력 — 1회 사용마다 1 row.
 * 부분 사용 흐름을 추적: 사용 시점 금액, 차감 후 잔액, 어느 주문에서 썼는지.
 */
@Entity
@Getter
@Table(name = "gifticon_usages",
        indexes = {
                @Index(name = "idx_gu_gifticon", columnList = "gifticon_id"),
                @Index(name = "idx_gu_order", columnList = "order_id")
        })
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GifticonUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "gifticon_id", nullable = false)
    private Long gifticonId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "used_amount", nullable = false)
    private Integer usedAmount;

    @Column(name = "balance_after", nullable = false)
    private Integer balanceAfter;

    @CreatedDate
    @Column(name = "used_at", updatable = false, nullable = false)
    private LocalDateTime usedAt;

    @Builder
    private GifticonUsage(Long gifticonId, Long orderId, Integer usedAmount, Integer balanceAfter) {
        this.gifticonId = gifticonId;
        this.orderId = orderId;
        this.usedAmount = usedAmount;
        this.balanceAfter = balanceAfter;
    }
}
