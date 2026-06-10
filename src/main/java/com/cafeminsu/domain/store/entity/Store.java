package com.cafeminsu.domain.store.entity;

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
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 매장.
 *
 * Soft delete 적용: DELETE 시 deleted_at만 채우고, 조회 시 자동으로 제외.
 *   @SQLDelete   : repository.delete() 호출 시 실제 UPDATE 쿼리로 변환
 *   @SQLRestriction: 모든 조회에 deleted_at IS NULL 조건 자동 추가
 */
@Entity
@Getter
@Table(name = "stores",
        indexes = {
                @Index(name = "idx_store_owner", columnList = "owner_id"),
                @Index(name = "idx_store_name", columnList = "name"),
                @Index(name = "idx_store_deleted", columnList = "deleted_at")
        })
@SQLDelete(sql = "UPDATE stores SET deleted_at = CURRENT_TIMESTAMP(6) WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Store extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 200)
    private String address;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(length = 30)
    private String phone;

    @Column(name = "business_hours", length = 100)
    private String businessHours;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    private Store(Long ownerId, String name, String address,
                  BigDecimal latitude, BigDecimal longitude,
                  String phone, String businessHours, String imageUrl) {
        this.ownerId = ownerId;
        this.name = name;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.phone = phone;
        this.businessHours = businessHours;
        this.imageUrl = imageUrl;
    }

    /**
     * PATCH 부분 수정 — null이 아닌 필드만 갱신.
     * 위치(latitude, longitude)는 일반적으로 한 번 등록하면 안 바뀌니까 수정 대상에서 제외.
     */
    public void updatePartial(String name, String address, String phone,
                              String businessHours, String imageUrl) {
        if (name != null) this.name = name;
        if (address != null) this.address = address;
        if (phone != null) this.phone = phone;
        if (businessHours != null) this.businessHours = businessHours;
        if (imageUrl != null) this.imageUrl = imageUrl;
    }

    public boolean isOwnedBy(Long userId) {
        return this.ownerId.equals(userId);
    }
}
