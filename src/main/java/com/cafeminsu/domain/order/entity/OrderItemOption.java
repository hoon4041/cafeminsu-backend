package com.cafeminsu.domain.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 주문 항목에 선택된 옵션. 가격은 스냅샷으로 저장 — 메뉴 옵션 가격 변동에 영향 안 받음.
 */
@Entity
@Getter
@Table(name = "order_item_options",
        indexes = @Index(name = "idx_oiopt_item", columnList = "order_item_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItemOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrderItem orderItem;

    /** 옵션 PK. 옵션이 삭제돼도 historical 데이터를 위해 보존. FK 안 걸음. */
    @Column(name = "menu_option_id", nullable = false)
    private Long menuOptionId;

    /** 주문 당시 추가 금액 스냅샷. 향후 옵션 가격이 바뀌어도 영수증·정산엔 영향 없음. */
    @Column(name = "option_price_snapshot", nullable = false)
    private Integer optionPriceSnapshot;

    @Builder
    private OrderItemOption(Long menuOptionId, Integer optionPriceSnapshot) {
        this.menuOptionId = menuOptionId;
        this.optionPriceSnapshot = optionPriceSnapshot == null ? 0 : optionPriceSnapshot;
    }

    /** 양방향 관계 세팅 (Order/OrderItem 빌더 내부에서 호출) */
    void attachTo(OrderItem orderItem) {
        this.orderItem = orderItem;
    }
}
