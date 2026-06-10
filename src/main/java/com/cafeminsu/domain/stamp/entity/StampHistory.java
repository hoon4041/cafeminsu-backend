package com.cafeminsu.domain.stamp.entity;

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
 * 적립 이력 — 적립 1회마다 1 row.
 * 어느 주문에서 얼마나 쌓였는지 추적.
 */
@Entity
@Getter
@Table(name = "stamp_histories",
        indexes = {
                @Index(name = "idx_sh_stamp", columnList = "stamp_id"),
                @Index(name = "idx_sh_order", columnList = "order_id")
        })
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StampHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stamp_id", nullable = false)
    private Long stampId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "earned_count", nullable = false)
    private Integer earnedCount;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @Builder
    private StampHistory(Long stampId, Long orderId, Integer earnedCount) {
        this.stampId = stampId;
        this.orderId = orderId;
        this.earnedCount = earnedCount;
    }
}
