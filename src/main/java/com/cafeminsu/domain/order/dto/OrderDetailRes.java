package com.cafeminsu.domain.order.dto;

import com.cafeminsu.domain.order.entity.Order;
import com.cafeminsu.domain.order.entity.OrderItem;
import com.cafeminsu.domain.order.entity.OrderItemOption;
import com.cafeminsu.domain.order.entity.OrderStatus;
import com.cafeminsu.domain.order.entity.OrderType;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * GET /api/orders/{orderId} 응답.
 * 결제(payment) 정보는 Payment 도메인 구현 후 채워 넣음 (현재 null).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderDetailRes(
        Long orderId,
        String orderNumber,
        Long storeId,
        String storeName,
        OrderType orderType,
        OrderStatus status,
        Integer totalAmount,
        String cancelReason,
        List<ItemRes> items,
        Object payment,            // TODO: Payment 도메인 구현 후 채움
        LocalDateTime createdAt
) {
    public static OrderDetailRes of(Order o, String storeName,
                                    Map<Long, String> menuNames,
                                    Map<Long, OptionInfo> optionInfos) {
        List<ItemRes> items = o.getItems().stream()
                .map(it -> ItemRes.of(it, menuNames, optionInfos))
                .toList();
        return new OrderDetailRes(
                o.getId(),
                o.getOrderNumber(),
                o.getStoreId(),
                storeName,
                o.getOrderType(),
                o.getStatus(),
                o.getTotalAmount(),
                o.getCancelReason(),
                items,
                null,
                o.getCreatedAt()
        );
    }

    public record ItemRes(
            Long menuId,
            String menuName,
            Integer quantity,
            Integer unitPrice,
            List<OptionRes> options,
            Integer subtotal
    ) {
        static ItemRes of(OrderItem item, Map<Long, String> menuNames, Map<Long, OptionInfo> optionInfos) {
            List<OptionRes> opts = item.getOptions().stream()
                    .map(o -> OptionRes.of(o, optionInfos))
                    .toList();
            return new ItemRes(
                    item.getMenuId(),
                    menuNames.getOrDefault(item.getMenuId(), "(삭제된 메뉴)"),
                    item.getQuantity(),
                    item.getUnitPrice(),
                    opts,
                    item.subtotal()
            );
        }
    }

    public record OptionRes(
            Long optionId,
            String optionGroup,
            String optionName,
            Integer optionPrice
    ) {
        static OptionRes of(OrderItemOption o, Map<Long, OptionInfo> optionInfos) {
            OptionInfo info = optionInfos.get(o.getMenuOptionId());
            return new OptionRes(
                    o.getMenuOptionId(),
                    info != null ? info.optionGroup() : null,
                    info != null ? info.optionName() : "(삭제된 옵션)",
                    o.getOptionPriceSnapshot()
            );
        }
    }

    /** 옵션 메타정보 캐리어 — service에서 만들어서 전달 */
    public record OptionInfo(String optionGroup, String optionName) {}
}
