package com.cafeminsu.domain.payment.entity;

public enum PaymentStatus {
    READY,      // prepare 직후, 결제 검증 전
    PAID,       // 검증 완료
    FAILED,     // 검증 실패 또는 결제 취소
    REFUNDED    // 환불 처리됨
}
