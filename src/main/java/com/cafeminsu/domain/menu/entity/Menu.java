package com.cafeminsu.domain.menu.entity;

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

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "menus",
        indexes = {
                @Index(name = "idx_menu_store", columnList = "store_id"),
                @Index(name = "idx_menu_category", columnList = "category"),
                @Index(name = "idx_menu_deleted", columnList = "deleted_at")
        })
@SQLDelete(sql = "UPDATE menus SET deleted_at = CURRENT_TIMESTAMP(6) WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Menu extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private Integer price;

    @Column(length = 50)
    private String category;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "is_available", nullable = false)
    private boolean isAvailable;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    private Menu(Long storeId, String name, String description, Integer price,
                 String category, String imageUrl, Boolean isAvailable) {
        this.storeId = storeId;
        this.name = name;
        this.description = description;
        this.price = price;
        this.category = category;
        this.imageUrl = imageUrl;
        // isAvailable null이면 기본 true
        this.isAvailable = isAvailable == null || isAvailable;
    }

    public void updatePartial(String name, String description, Integer price,
                              String category, String imageUrl) {
        if (name != null) this.name = name;
        if (description != null) this.description = description;
        if (price != null) this.price = price;
        if (category != null) this.category = category;
        if (imageUrl != null) this.imageUrl = imageUrl;
    }

    public void changeAvailability(boolean isAvailable) {
        this.isAvailable = isAvailable;
    }
}
