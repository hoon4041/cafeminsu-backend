package com.cafeminsu.domain.order.entity;

import com.cafeminsu.global.common.BaseEntity;
import com.cafeminsu.global.common.BaseResponseStatus;
import com.cafeminsu.global.exception.BaseException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Table(name = "orders",
        indexes = {
                @Index(name = "idx_order_user", columnList = "user_id"),
                @Index(name = "idx_order_store", columnList = "store_id"),
                @Index(name = "idx_order_status", columnList = "status")
        },
        // 같은 매장에서 같은 주문번호 두 번 발급되지 않게
        uniqueConstraints = @UniqueConstraint(name = "uk_order_store_number",
                columnNames = {"store_id", "order_number"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 키오스크 비회원 주문은 NULL 허용 */
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "order_number", nullable = false, length = 10)
    private String orderNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, length = 20)
    private OrderType orderType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false)
    private Integer totalAmount;

    @Column(name = "cancel_reason", length = 200)
    private String cancelReason;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @Builder
    private Order(Long userId, Long storeId, String orderNumber,
                  OrderType orderType,
                  Integer totalAmount, List<OrderItem> items) {
        this.userId = userId;
        this.storeId = storeId;
        this.orderNumber = orderNumber;
        this.orderType = orderType;
        this.totalAmount = totalAmount;
        this.status = OrderStatus.PENDING;
        if (items != null) {
            for (OrderItem item : items) {
                item.attachTo(this);
                this.items.add(item);
            }
        }
    }

    /* ===== 상태 전이 (상태 머신) ===== */
    public void accept() {
        if (!status.canAccept()) {
            throw new BaseException(BaseResponseStatus.INVALID_ORDER_STATUS);
        }
        this.status = OrderStatus.ACCEPTED;
    }

    public void markReady() {
        if (!status.canReady()) {
            throw new BaseException(BaseResponseStatus.INVALID_ORDER_STATUS);
        }
        this.status = OrderStatus.READY;
    }

    public void complete() {
        if (!status.canComplete()) {
            throw new BaseException(BaseResponseStatus.INVALID_ORDER_STATUS);
        }
        this.status = OrderStatus.DONE;
    }

    public void cancel(String reason) {
        if (!status.canCancel()) {
            throw new BaseException(BaseResponseStatus.ORDER_CANNOT_BE_CANCELLED);
        }
        this.status = OrderStatus.CANCELLED;
        this.cancelReason = reason;
    }

    public boolean isPlacedBy(Long userId) {
        return this.userId != null && this.userId.equals(userId);
    }
}
