package com.cafeminsu.domain.order.repository;

import com.cafeminsu.domain.order.entity.Order;
import com.cafeminsu.domain.order.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * 상세 조회 — items까지 fetch join.
     * options는 lazy로 두고, service에서 access 시점에 로드 (트랜잭션 안에서 호출됨).
     *
     * 두 컬렉션(items, items.options)을 모두 fetch join하면 MultipleBagFetchException 발생.
     * Hibernate가 List를 'bag'으로 취급하기 때문.
     */
    @EntityGraph(attributePaths = {"items"})
    Optional<Order> findWithItemsById(Long id);

    /** 내 주문 내역 — status 필터 옵션 */
    Page<Order> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);

    Page<Order> findByUserIdAndStatusOrderByIdDesc(Long userId, OrderStatus status, Pageable pageable);

    /** 매장 주문 목록 — status·날짜 필터. items도 함께 로드. */
    @EntityGraph(attributePaths = {"items"})
    @Query("""
            SELECT DISTINCT o FROM Order o
            WHERE o.storeId = :storeId
              AND (:status IS NULL OR o.status = :status)
              AND (:from IS NULL OR o.createdAt >= :from)
              AND (:to   IS NULL OR o.createdAt <  :to)
            ORDER BY o.id DESC
            """)
    List<Order> findStoreOrders(@Param("storeId") Long storeId,
                                @Param("status") OrderStatus status,
                                @Param("from") LocalDateTime from,
                                @Param("to") LocalDateTime to);

    /** 같은 매장에서 같은 주문번호 존재 여부 — 채번 충돌 방지용 */
    boolean existsByStoreIdAndOrderNumber(Long storeId, String orderNumber);
}
