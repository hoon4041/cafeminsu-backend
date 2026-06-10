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

/**
 * 메뉴 옵션. (메뉴 옵션은 일반적으로 삭제 빈도가 낮고, 주문 시 스냅샷으로 가격을 저장하므로 hard delete 가능.
 * 하지만 안전하게 가려면 soft delete도 고려 가능 — 현재는 hard delete.)
 *
 * option_group 예: size, temp, shot, syrup
 * option_name  예: L, ICE, +1샷, 바닐라
 */
@Entity
@Getter
@Table(name = "menu_options",
        indexes = {
                @Index(name = "idx_menuopt_menu", columnList = "menu_id"),
                @Index(name = "idx_menuopt_group", columnList = "option_group")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MenuOption extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "menu_id", nullable = false)
    private Long menuId;

    @Column(name = "option_group", nullable = false, length = 30)
    private String optionGroup;

    @Column(name = "option_name", nullable = false, length = 50)
    private String optionName;

    @Column(name = "additional_price", nullable = false)
    private Integer additionalPrice;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Builder
    private MenuOption(Long menuId, String optionGroup, String optionName,
                       Integer additionalPrice, Boolean isDefault) {
        this.menuId = menuId;
        this.optionGroup = optionGroup;
        this.optionName = optionName;
        this.additionalPrice = additionalPrice == null ? 0 : additionalPrice;
        this.isDefault = isDefault != null && isDefault;
    }

    public void updatePartial(String optionGroup, String optionName,
                              Integer additionalPrice, Boolean isDefault) {
        if (optionGroup != null) this.optionGroup = optionGroup;
        if (optionName != null) this.optionName = optionName;
        if (additionalPrice != null) this.additionalPrice = additionalPrice;
        if (isDefault != null) this.isDefault = isDefault;
    }
}
