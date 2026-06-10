package com.cafeminsu.domain.order.dto;

import com.cafeminsu.domain.order.entity.Order;
import com.cafeminsu.domain.order.entity.OrderStatus;

public record OrderCreateRes(
        Long orderId,
        String orderNumber,
        Integer totalAmount,
        OrderStatus status
) {
    public static OrderCreateRes from(Order order) {
        return new OrderCreateRes(order.getId(), order.getOrderNumber(),
                order.getTotalAmount(), order.getStatus());
    }
}
