package com.cafeminsu.domain.stamp.entity;

import com.cafeminsu.global.common.BaseEntity;
import com.cafeminsu.global.common.BaseResponseStatus;
import com.cafeminsu.global.exception.BaseException;
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

/**
 * 사용자×매장 = 1 row. 매장별로 누적 카운트만 보관.
 */
@Entity
@Getter
@Table(name = "stamps",
        uniqueConstraints = @UniqueConstraint(name = "uk_stamp_user_store",
                columnNames = {"user_id", "store_id"}),
        indexes = {
                @Index(name = "idx_stamp_user", columnList = "user_id"),
                @Index(name = "idx_stamp_store", columnList = "store_id")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Stamp extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(nullable = false)
    private Integer count;

    @Builder
    private Stamp(Long userId, Long storeId) {
        this.userId = userId;
        this.storeId = storeId;
        this.count = 0;
    }

    public void earn(int amount) {
        if (amount <= 0) return;
        this.count += amount;
    }

    /** 보상 전환 시 스탬프 차감. 잔여가 부족하면 예외. */
    public void redeem(int amount) {
        if (amount <= 0) return;
        if (this.count < amount) {
            throw new BaseException(BaseResponseStatus.INVALID_REQUEST, "차감할 스탬프가 부족합니다.");
        }
        this.count -= amount;
    }
}
