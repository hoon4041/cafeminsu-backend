package com.cafeminsu.domain.order.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Table(name = "order_items",
        indexes = @Index(name = "idx_oitem_order", columnList = "order_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /** 메뉴 PK. 메뉴가 삭제(soft)돼도 historical 보존. */
    @Column(name = "menu_id", nullable = false)
    private Long menuId;

    @Column(nullable = false)
    private Integer quantity;

    /** 주문 당시 단가(메뉴 가격 스냅샷). */
    @Column(name = "unit_price", nullable = false)
    private Integer unitPrice;

    @OneToMany(mappedBy = "orderItem", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItemOption> options = new ArrayList<>();

    @Builder
    private OrderItem(Long menuId, Integer quantity, Integer unitPrice, List<OrderItemOption> options) {
        this.menuId = menuId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        if (options != null) {
            for (OrderItemOption opt : options) {
                opt.attachTo(this);
                this.options.add(opt);
            }
        }
    }

    void attachTo(Order order) {
        this.order = order;
    }

    /** 이 항목의 소계 = (단가 + 옵션 합) × 수량 */
    public int subtotal() {
        int optSum = options.stream()
                .mapToInt(OrderItemOption::getOptionPriceSnapshot)
                .sum();
        return (unitPrice + optSum) * quantity;
    }
}
