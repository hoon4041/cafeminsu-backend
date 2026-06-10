package com.cafeminsu.domain.menu.dto;

import com.cafeminsu.domain.menu.entity.Menu;
import com.cafeminsu.domain.menu.entity.MenuOption;

import java.util.List;

/**
 * GET /api/menus/{menuId} 응답 — 옵션 포함.
 * 주문 화면에서 호출.
 */
public record MenuDetailRes(
        Long id,
        String name,
        String description,
        Integer price,
        String category,
        String imageUrl,
        boolean isAvailable,
        List<OptionRes> options
) {
    public static MenuDetailRes from(Menu menu, List<MenuOption> options) {
        return new MenuDetailRes(
                menu.getId(),
                menu.getName(),
                menu.getDescription(),
                menu.getPrice(),
                menu.getCategory(),
                menu.getImageUrl(),
                menu.isAvailable(),
                options.stream().map(OptionRes::from).toList()
        );
    }

    public record OptionRes(
            Long id,
            String group,
            String name,
            Integer additionalPrice,
            boolean isDefault
    ) {
        public static OptionRes from(MenuOption o) {
            return new OptionRes(
                    o.getId(),
                    o.getOptionGroup(),
                    o.getOptionName(),
                    o.getAdditionalPrice(),
                    o.isDefault()
            );
        }
    }
}
