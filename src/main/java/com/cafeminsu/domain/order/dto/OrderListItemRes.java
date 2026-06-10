package com.cafeminsu.domain.order.dto;

import com.cafeminsu.domain.order.entity.Order;
import com.cafeminsu.domain.order.entity.OrderStatus;

import java.time.LocalDateTime;

/**
 * GET /api/orders/my 응답 항목.
 * 매장명은 service에서 조회해서 매핑.
 */
public record OrderListItemRes(
        Long orderId,
        String orderNumber,
        String storeName,
        Integer totalAmount,
        OrderStatus status,
        LocalDateTime createdAt
) {
    public static OrderListItemRes of(Order o, String storeName) {
        return new OrderListItemRes(
                o.getId(), o.getOrderNumber(), storeName,
                o.getTotalAmount(), o.getStatus(), o.getCreatedAt()
        );
    }
}
