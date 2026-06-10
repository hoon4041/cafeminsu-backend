package com.cafeminsu.domain.payment.dto;

import com.cafeminsu.domain.payment.entity.Payment;
import com.cafeminsu.domain.payment.entity.PaymentMethod;
import com.cafeminsu.domain.payment.entity.PaymentStatus;

import java.time.LocalDateTime;

public record PaymentDetailRes(
        Long paymentId,
        Long orderId,
        PaymentMethod method,
        Integer amount,
        PaymentStatus status,
        LocalDateTime paidAt
) {
    public static PaymentDetailRes from(Payment p) {
        return new PaymentDetailRes(
                p.getId(), p.getOrderId(), p.getMethod(),
                p.getAmount(), p.getStatus(), p.getPaidAt()
        );
    }
}
