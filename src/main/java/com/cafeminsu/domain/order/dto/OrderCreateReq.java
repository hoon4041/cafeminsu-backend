package com.cafeminsu.domain.order.dto;

import com.cafeminsu.domain.order.entity.OrderType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record OrderCreateReq(
        @NotNull(message = "storeId는 필수입니다.")
        Long storeId,

        @NotNull(message = "orderType은 필수입니다 (MOBILE / KIOSK).")
        OrderType orderType,

        @NotEmpty(message = "주문 항목이 비어 있습니다.")
        @Valid
        List<Item> items
) {
    public record Item(
            @NotNull Long menuId,
            @NotNull @Min(1) Integer quantity,
            List<Long> optionIds
    ) {
    }
}
