package com.cafeminsu.domain.payment.repository;

import com.cafeminsu.domain.payment.entity.Payment;
import com.cafeminsu.domain.payment.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByMerchantUid(String merchantUid);

    /** 한 주문의 모든 Payment row (분할결제면 2개) */
    List<Payment> findAllByOrderId(Long orderId);

    /**
     * 매장 결제 내역 — PAID만 집계. Payment는 Order를 거쳐 store와 연결되므로 JPQL JOIN.
     */
    @Query("""
            SELECT p FROM Payment p, com.cafeminsu.domain.order.entity.Order o
            WHERE p.orderId = o.id
              AND o.storeId = :storeId
              AND p.status = :status
              AND (:from IS NULL OR p.paidAt >= :from)
              AND (:to   IS NULL OR p.paidAt <  :to)
            ORDER BY p.id DESC
            """)
    List<Payment> findStorePayments(@Param("storeId") Long storeId,
                                    @Param("status") PaymentStatus status,
                                    @Param("from") LocalDateTime from,
                                    @Param("to") LocalDateTime to);
}
