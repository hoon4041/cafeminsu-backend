package com.cafeminsu.domain.stamp.entity;

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
}
