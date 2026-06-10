package com.cafeminsu.domain.payment.dto;

import com.cafeminsu.domain.payment.entity.Payment;
import com.cafeminsu.domain.payment.entity.PaymentMethod;

import java.time.LocalDateTime;
import java.util.List;

/**
 * GET /api/stores/{storeId}/payments 응답.
 * total: 기간 동안의 PAID 합계 (정산용).
 */
public record StorePaymentsRes(
        Long total,
        List<Item> payments
) {
    public static StorePaymentsRes of(List<Payment> payments) {
        long total = payments.stream().mapToLong(Payment::getAmount).sum();
        List<Item> items = payments.stream().map(Item::from).toList();
        return new StorePaymentsRes(total, items);
    }

    public record Item(
            Long paymentId,
            Long orderId,
            PaymentMethod method,
            Integer amount,
            LocalDateTime paidAt
    ) {
        static Item from(Payment p) {
            return new Item(p.getId(), p.getOrderId(), p.getMethod(),
                    p.getAmount(), p.getPaidAt());
        }
    }
}
