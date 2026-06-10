package com.cafeminsu.domain.order.entity;

/**
 * 주문 진행 상태.
 *
 * 정상 전이: PENDING → ACCEPTED → READY → DONE
 * 취소: PENDING / ACCEPTED → CANCELLED
 *
 * READY 이후엔 취소 불가 — 이미 음료가 만들어진 상태.
 */
public enum OrderStatus {
    PENDING,
    ACCEPTED,
    READY,
    DONE,
    CANCELLED;

    public boolean canCancel() {
        return this == PENDING || this == ACCEPTED;
    }

    public boolean canAccept() {
        return this == PENDING;
    }

    public boolean canReady() {
        return this == ACCEPTED;
    }

    public boolean canComplete() {
        return this == READY;
    }
}
