package com.cafeminsu.domain.menu.dto;

import com.cafeminsu.domain.menu.entity.Menu;

public record MenuListItemRes(
        Long id,
        String name,
        Integer price,
        String category,
        String imageUrl,
        boolean isAvailable
) {
    public static MenuListItemRes from(Menu menu) {
        return new MenuListItemRes(
                menu.getId(),
                menu.getName(),
                menu.getPrice(),
                menu.getCategory(),
                menu.getImageUrl(),
                menu.isAvailable()
        );
    }
}
