package com.cafeminsu.domain.payment.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * GET /api/stores/{storeId}/sales-summary 응답 — 점주 매출 대시보드.
 *
 * 모두 PAID 결제(기간 내 paidAt 기준)만 집계한다. 환불(REFUNDED)·미결제는 제외.
 *
 *   totalSales : 기간 내 PAID 결제금액 합 (StorePaymentsRes.total과 동일 기준)
 *   dailySales : 일자별 매출 버킷 (paidAt 날짜 기준)
 *   topMenus   : 메뉴별 판매 랭킹 (판매금액 내림차순). 옵션 추가금은 제외한 메뉴 단가 기준.
 */
public record SalesSummaryRes(
        long totalSales,
        List<DailySales> dailySales,
        List<TopMenu> topMenus
) {
    /** 일자별 매출. amount=그 날 PAID 결제금액 합, orderCount=그 날 결제된 주문 수(분할결제 중복 제외). */
    public record DailySales(
            LocalDate date,
            long amount,
            long orderCount
    ) {}

    /** 메뉴별 판매. quantity=판매 수량 합, amount=메뉴 단가×수량 합(옵션 제외). */
    public record TopMenu(
            Long menuId,
            String name,
            long quantity,
            long amount
    ) {}
}
