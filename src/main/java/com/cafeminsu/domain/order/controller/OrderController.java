package com.cafeminsu.domain.order.controller;

import com.cafeminsu.domain.order.dto.OrderCancelReq;
import com.cafeminsu.domain.order.dto.OrderCreateReq;
import com.cafeminsu.domain.order.dto.OrderCreateRes;
import com.cafeminsu.domain.order.dto.OrderDetailRes;
import com.cafeminsu.domain.order.dto.OrderListItemRes;
import com.cafeminsu.domain.order.dto.OrderStatusRes;
import com.cafeminsu.domain.order.dto.StoreOrderItemRes;
import com.cafeminsu.domain.order.entity.OrderStatus;
import com.cafeminsu.domain.order.service.OrderService;
import com.cafeminsu.global.security.LoginUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "4. Order", description = "주문 API")
@RestController
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /* 1. 주문 생성 */
    @Operation(summary = "주문 생성",
            description = "결제 전 PENDING 상태로 생성. 가격은 서버에서 메뉴 DB로 재계산.")
    @PostMapping("/api/orders")
    public OrderCreateRes create(@LoginUserId Long userId,
                                 @Valid @RequestBody OrderCreateReq req) {
        return orderService.createOrder(userId, req);
    }

    /* 2. 내 주문 내역 */
    @Operation(summary = "내 주문 내역", description = "status 필터 옵션 (PENDING/ACCEPTED/READY/DONE/CANCELLED)")
    @GetMapping("/api/orders/my")
    public List<OrderListItemRes> myOrders(
            @LoginUserId Long userId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return orderService.getMyOrders(userId, status, page, size);
    }

    /* 3-1. 최근 주문 5건 (홈 화면) */
    @Operation(summary = "최근 주문 5건", description = "홈 화면 빠른 표시용. 상태 무관 최신순 5건.")
    @GetMapping("/api/orders/my/recent")
    public List<OrderListItemRes> myRecentOrders(@LoginUserId Long userId) {
        return orderService.getRecentOrders(userId);
    }

    /* 4. 주문 상세 (본인 또는 매장 점주) */
    @Operation(summary = "주문 상세")
    @GetMapping("/api/orders/{orderId}")
    public OrderDetailRes detail(@LoginUserId Long userId,
                                 @PathVariable Long orderId) {
        return orderService.getOrderDetail(userId, orderId);
    }

    /* 5. 매장 주문 목록 (점주) */
    @Operation(summary = "매장 주문 목록 (점주)", description = "매장 앱 메인 리스트. status·date 필터 옵션.")
    @GetMapping("/api/stores/{storeId}/orders")
    public List<StoreOrderItemRes> storeOrders(
            @LoginUserId Long userId,
            @PathVariable Long storeId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return orderService.getStoreOrders(userId, storeId, status, date);
    }

    /* 6. 주문 수락 (점주) */
    @Operation(summary = "주문 수락", description = "PENDING → ACCEPTED. 고객 FCM 자동 발송(예정).")
    @PatchMapping("/api/orders/{orderId}/accept")
    public OrderStatusRes accept(@LoginUserId Long userId,
                                 @PathVariable Long orderId) {
        return orderService.acceptOrder(userId, orderId);
    }

    /* 7. 준비 완료 (점주) */
    @Operation(summary = "준비 완료", description = "ACCEPTED → READY. 고객 FCM 자동 발송(예정).")
    @PatchMapping("/api/orders/{orderId}/ready")
    public OrderStatusRes ready(@LoginUserId Long userId,
                                @PathVariable Long orderId) {
        return orderService.markReady(userId, orderId);
    }

    /* 8. 픽업 완료 (점주) */
    @Operation(summary = "픽업 완료",
            description = "READY → DONE. 스탬프 자동 적립 트리거(예정).")
    @PatchMapping("/api/orders/{orderId}/complete")
    public OrderStatusRes complete(@LoginUserId Long userId,
                                   @PathVariable Long orderId) {
        return orderService.completeOrder(userId, orderId);
    }

    /* 9. 주문 취소 (고객 본인 또는 매장 점주) */
    @Operation(summary = "주문 취소",
            description = "PENDING / ACCEPTED 상태에서만 가능. 결제 환불 처리 포함(예정).")
    @PostMapping("/api/orders/{orderId}/cancel")
    public void cancel(@LoginUserId Long userId,
                       @PathVariable Long orderId,
                       @RequestBody(required = false) OrderCancelReq req) {
        orderService.cancelOrder(userId, orderId, req);
    }

    /* 10. 빠른 재주문 */
    @Operation(summary = "빠른 재주문", description = "이전 주문 구성으로 새 PENDING 주문 생성.")
    @PostMapping("/api/orders/reorder/{previousOrderId}")
    public OrderCreateRes reorder(@LoginUserId Long userId,
                                  @PathVariable Long previousOrderId) {
        return orderService.reorder(userId, previousOrderId);
    }
}
