package com.cafeminsu.domain.gifticon.entity;

public enum GifticonStatus {
    UNUSED,     // 잔액 = 금액. 한 번도 안 씀.
    PARTIAL,    // 일부 사용. 잔액 > 0.
    USED,       // 잔액 = 0. 모두 사용.
    EXPIRED     // 유효기간 만료.
}
