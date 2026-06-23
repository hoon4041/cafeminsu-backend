package com.cafeminsu.domain.order.repository;

import com.cafeminsu.domain.order.entity.OrderItem;
import com.cafeminsu.domain.payment.entity.PaymentStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    /** 메뉴별 판매 집계 행 (인터페이스 프로젝션). */
    interface TopMenuRow {
        Long getMenuId();
        String getName();      // soft-delete된 메뉴면 null
        long getQuantity();
        long getAmount();
    }

    /**
     * 매장 메뉴별 판매 랭킹 — 기간 내 PAID 결제된 주문의 OrderItem만 집계.
     *
     * 분할결제(한 주문에 Payment row 2개)로 인한 OrderItem 중복 집계를 막기 위해
     * Payment를 직접 조인하지 않고, "PAID 결제가 있는 주문 ID" 서브쿼리(DISTINCT)로 거른다.
     * 금액은 옵션 추가금을 뺀 메뉴 단가 기준(unitPrice × quantity) — 단일 테이블 집계라 명확.
     * Menu는 entity join(LEFT) — soft-delete된 메뉴는 name이 null로 나온다(historical 보존).
     */
    @Query("""
            SELECT oi.menuId AS menuId,
                   m.name AS name,
                   SUM(oi.quantity) AS quantity,
                   SUM(oi.unitPrice * oi.quantity) AS amount
            FROM OrderItem oi
                JOIN oi.order o
                LEFT JOIN com.cafeminsu.domain.menu.entity.Menu m ON m.id = oi.menuId
            WHERE o.storeId = :storeId
              AND o.id IN (
                  SELECT DISTINCT p.orderId FROM com.cafeminsu.domain.payment.entity.Payment p
                  WHERE p.status = :status
                    AND (:from IS NULL OR p.paidAt >= :from)
                    AND (:to   IS NULL OR p.paidAt <  :to)
              )
            GROUP BY oi.menuId, m.name
            ORDER BY SUM(oi.unitPrice * oi.quantity) DESC
            """)
    List<TopMenuRow> findTopMenus(@Param("storeId") Long storeId,
                                  @Param("status") PaymentStatus status,
                                  @Param("from") LocalDateTime from,
                                  @Param("to") LocalDateTime to,
                                  Pageable pageable);
}
