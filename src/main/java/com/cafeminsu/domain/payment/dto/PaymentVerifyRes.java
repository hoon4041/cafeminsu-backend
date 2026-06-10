package com.cafeminsu.domain.payment.dto;

import com.cafeminsu.domain.payment.entity.PaymentStatus;

public record PaymentVerifyRes(
        Long paymentId,
        PaymentStatus status
) {
}
