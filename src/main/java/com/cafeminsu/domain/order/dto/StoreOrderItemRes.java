package com.cafeminsu.domain.order.dto;

import com.cafeminsu.domain.order.entity.Order;
import com.cafeminsu.domain.order.entity.OrderItem;
import com.cafeminsu.domain.order.entity.OrderStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * GET /api/stores/{storeId}/orders 응답 항목 (점주용).
 * 메뉴 정보를 함께 표시 — 매장 앱 메인 리스트.
 */
public record StoreOrderItemRes(
        Long orderId,
        String orderNumber,
        OrderStatus status,
        Integer totalAmount,
        List<MenuSummary> items,
        LocalDateTime createdAt
) {
    public static StoreOrderItemRes of(Order o, Map<Long, String> menuNames) {
        List<MenuSummary> menus = o.getItems().stream()
                .map(it -> new MenuSummary(
                        it.getMenuId(),
                        menuNames.getOrDefault(it.getMenuId(), "(삭제된 메뉴)"),
                        it.getQuantity()
                ))
                .toList();
        return new StoreOrderItemRes(
                o.getId(), o.getOrderNumber(), o.getStatus(),
                o.getTotalAmount(), menus, o.getCreatedAt()
        );
    }

    public record MenuSummary(Long menuId, String menuName, Integer quantity) {}

    /** OrderItem 단독으로 변환할 때도 쓸 수 있게 헬퍼 */
    public static MenuSummary toMenuSummary(OrderItem item, String name) {
        return new MenuSummary(item.getMenuId(), name, item.getQuantity());
    }
}
