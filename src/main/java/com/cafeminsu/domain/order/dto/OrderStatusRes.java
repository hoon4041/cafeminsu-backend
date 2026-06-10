package com.cafeminsu.domain.order.dto;

import com.cafeminsu.domain.order.entity.OrderStatus;

public record OrderStatusRes(
        OrderStatus status
) {
}
