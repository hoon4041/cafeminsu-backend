package com.cafeminsu.domain.gifticon.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record GifticonUseReq(
        @NotNull Long orderId,
        @NotNull @Min(1) Integer usedAmount
) {
}
