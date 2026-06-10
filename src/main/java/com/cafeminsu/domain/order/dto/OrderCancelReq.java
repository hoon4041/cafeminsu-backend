package com.cafeminsu.domain.order.dto;

import jakarta.validation.constraints.Size;

public record OrderCancelReq(
        @Size(max = 200, message = "사유는 200자 이내여야 합니다.")
        String reason
) {
}
