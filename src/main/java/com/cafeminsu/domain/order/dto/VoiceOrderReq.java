package com.cafeminsu.domain.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record VoiceOrderReq(
        @NotNull Long storeId,
        @NotBlank(message = "audioText는 필수입니다.")
        String audioText
) {
}
