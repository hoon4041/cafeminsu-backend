package com.cafeminsu.domain.order.service;

import com.cafeminsu.domain.menu.entity.Menu;
import com.cafeminsu.domain.menu.entity.MenuOption;
import com.cafeminsu.domain.menu.repository.MenuOptionRepository;
import com.cafeminsu.domain.menu.repository.MenuRepository;
import com.cafeminsu.domain.order.dto.OrderCancelReq;
import com.cafeminsu.domain.order.dto.OrderCreateReq;
import com.cafeminsu.domain.order.dto.OrderCreateRes;
import com.cafeminsu.domain.order.dto.OrderDetailRes;
import com.cafeminsu.domain.order.dto.OrderStatusRes;
import com.cafeminsu.domain.order.dto.StoreOrderItemRes;
import com.cafeminsu.domain.order.entity.Order;
import com.cafeminsu.domain.order.entity.OrderItem;
import com.cafeminsu.domain.order.entity.OrderItemOption;
import com.cafeminsu.domain.order.entity.OrderStatus;
import com.cafeminsu.domain.order.repository.OrderRepository;
import com.cafeminsu.domain.store.entity.Store;
import com.cafeminsu.domain.store.repository.StoreRepository;
import com.cafeminsu.global.common.BaseResponseStatus;
import com.cafeminsu.global.exception.BaseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    /** 홈 화면 '최근 주문' 빠른 표시용 노출 건수. */
    private static final int RECENT_ORDER_LIMIT = 5;

    private final OrderRepository orderRepository;
    private final StoreRepository storeRepository;
    private final MenuRepository menuRepository;
    private final MenuOptionRepository menuOptionRepository;
    private final OrderNumberGenerator orderNumberGenerator;

    // 도메인 연동 — 상태 전이 시 자동 호출
    private final com.cafeminsu.domain.notification.service.NotificationService notificationService;
    private final com.cafeminsu.domain.stamp.service.StampService stampService;
    private final com.cafeminsu.domain.stamp.service.DrinkCategoryPolicy drinkCategoryPolicy;

    /* =========================================================
     * 1) 주문 생성 — 서버 사이드 가격 재계산
     * ========================================================= */
    @Transactional
    public OrderCreateRes createOrder(Long userId, OrderCreateReq req) {
        // 매장 존재 확인
        Store store = storeRepository.findById(req.storeId())
                .orElseThrow(() -> new BaseException(BaseResponseStatus.STORE_NOT_FOUND));

        // 모든 메뉴를 한 번에 조회 (N+1 방지)
        List<Long> menuIds = req.items().stream().map(OrderCreateReq.Item::menuId).distinct().toList();
        Map<Long, Menu> menuMap = menuRepository.findAllById(menuIds).stream()
                .collect(Collectors.toMap(Menu::getId, Function.identity()));

        // 모든 옵션도 한 번에
        Set<Long> optionIds = req.items().stream()
                .flatMap(it -> it.optionIds() == null ? java.util.stream.Stream.empty() : it.optionIds().stream())
                .collect(Collectors.toSet());
        Map<Long, MenuOption> optionMap = optionIds.isEmpty()
                ? Map.of()
                : menuOptionRepository.findAllById(optionIds).stream()
                .collect(Collectors.toMap(MenuOption::getId, Function.identity()));

        int totalAmount = 0;
        List<OrderItem> orderItems = new ArrayList<>();

        for (OrderCreateReq.Item item : req.items()) {
            Menu menu = menuMap.get(item.menuId());
            if (menu == null) {
                throw new BaseException(BaseResponseStatus.MENU_NOT_FOUND);
            }
            // 메뉴는 같은 매장에 속해야 함 — 다른 매장 메뉴 끼워넣기 방지
            if (!menu.getStoreId().equals(store.getId())) {
                throw new BaseException(BaseResponseStatus.MENU_NOT_FOUND);
            }
            if (!menu.isAvailable()) {
                throw new BaseException(BaseResponseStatus.MENU_NOT_AVAILABLE);
            }

            List<OrderItemOption> orderOptions = new ArrayList<>();
            if (item.optionIds() != null) {
                for (Long optId : item.optionIds()) {
                    MenuOption opt = optionMap.get(optId);
                    if (opt == null) {
                        throw new BaseException(BaseResponseStatus.MENU_OPTION_NOT_FOUND);
                    }
                    // 옵션은 해당 메뉴의 옵션이어야 함
                    if (!opt.getMenuId().equals(menu.getId())) {
                        throw new BaseException(BaseResponseStatus.MENU_OPTION_NOT_FOUND);
                    }
                    orderOptions.add(OrderItemOption.builder()
                            .menuOptionId(opt.getId())
                            .optionPriceSnapshot(opt.getAdditionalPrice())
                            .build());
                }
            }

            OrderItem orderItem = OrderItem.builder()
                    .menuId(menu.getId())
                    .quantity(item.quantity())
                    .unitPrice(menu.getPrice())  // 가격은 서버 DB에서
                    .options(orderOptions)
                    .build();
            totalAmount += orderItem.subtotal();
            orderItems.add(orderItem);
        }

        String orderNumber = orderNumberGenerator.generate(store.getId());

        Order order = Order.builder()
                .userId(userId)
                .storeId(store.getId())
                .orderNumber(orderNumber)
                .orderType(req.orderType())
                .totalAmount(totalAmount)
                .items(orderItems)
                .build();
        Order saved = orderRepository.save(order);
        log.info("[Order] created id={} number={} total={}", saved.getId(), saved.getOrderNumber(), totalAmount);
        return OrderCreateRes.from(saved);
    }

    /* =========================================================
     * 2) 내 주문 내역 — 메뉴 항목(items)·옵션까지 상세 포함
     * ========================================================= */
    public List<OrderDetailRes> getMyOrders(Long userId, OrderStatus status, int page, int size) {
        var pageable = PageRequest.of(page, size);
        var orders = (status == null)
                ? orderRepository.findByUserIdOrderByIdDesc(userId, pageable)
                : orderRepository.findByUserIdAndStatusOrderByIdDesc(userId, status, pageable);

        return toDetailResponses(orders.getContent());
    }

    /* =========================================================
     * 3-1) 최근 주문 N건 — 홈 화면용 (상태 무관, 최신순 5건). items·옵션 포함
     * ========================================================= */
    public List<OrderDetailRes> getRecentOrders(Long userId) {
        var orders = orderRepository.findByUserIdOrderByIdDesc(
                userId, PageRequest.of(0, RECENT_ORDER_LIMIT));

        return toDetailResponses(orders.getContent());
    }

    /* =========================================================
     * 4) 주문 상세
     * ========================================================= */
    public OrderDetailRes getOrderDetail(Long userId, Long orderId) {
        Order order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.ORDER_NOT_FOUND));

        // 권한: 본인 주문 또는 해당 매장 점주만 조회 가능
        boolean isOwner = isStoreOwner(order.getStoreId(), userId);
        boolean isCustomer = order.isPlacedBy(userId);
        if (!isOwner && !isCustomer) {
            throw new BaseException(BaseResponseStatus.ACCESS_DENIED);
        }

        String storeName = storeRepository.findById(order.getStoreId())
                .map(Store::getName).orElse("(삭제된 매장)");
        var menuNames = menuNames(order);
        var optionInfos = optionInfos(order);
        return OrderDetailRes.of(order, storeName, menuNames, optionInfos);
    }

    /* =========================================================
     * 5) 매장 주문 목록 (점주)
     * ========================================================= */
    public List<StoreOrderItemRes> getStoreOrders(Long userId, Long storeId,
                                                  OrderStatus status, LocalDate date) {
        verifyStoreOwner(storeId, userId);

        LocalDateTime from = date != null ? date.atStartOfDay() : null;
        LocalDateTime to   = date != null ? date.plusDays(1).atStartOfDay() : null;

        List<Order> orders = orderRepository.findStoreOrders(storeId, status, from, to);
        // 메뉴명 조회
        Set<Long> menuIds = orders.stream()
                .flatMap(o -> o.getItems().stream().map(OrderItem::getMenuId))
                .collect(Collectors.toSet());
        Map<Long, String> menuNames = menuIds.isEmpty() ? Map.of()
                : menuRepository.findAllById(menuIds).stream()
                .collect(Collectors.toMap(Menu::getId, Menu::getName));
        return orders.stream().map(o -> StoreOrderItemRes.of(o, menuNames)).toList();
    }

    /* =========================================================
     * 6~8) 상태 전이 — 점주 본인 매장만
     * ========================================================= */
    @Transactional
    public OrderStatusRes acceptOrder(Long userId, Long orderId) {
        Order order = findOwnedOrder(orderId, userId);
        order.accept();
        notificationService.sendOrderAccepted(order);
        return new OrderStatusRes(order.getStatus());
    }

    @Transactional
    public OrderStatusRes markReady(Long userId, Long orderId) {
        Order order = findOwnedOrder(orderId, userId);
        order.markReady();
        notificationService.sendOrderReady(order);
        return new OrderStatusRes(order.getStatus());
    }

    @Transactional
    public OrderStatusRes completeOrder(Long userId, Long orderId) {
        Order order = findOwnedOrder(orderId, userId);
        order.complete();
        // 회원 주문만 스탬프 적립 (키오스크 비회원 주문은 userId가 null)
        // 적립 기준: 음료 1잔당 1개 → 주문의 음료 수량 합
        if (order.getUserId() != null) {
            int drinkCount = countDrinkStamps(order);
            stampService.earnFromOrder(order.getUserId(), order.getStoreId(), order.getId(), drinkCount);
        }
        return new OrderStatusRes(order.getStatus());
    }

    /* =========================================================
     * 9) 주문 취소 — 고객 본인 또는 매장 점주
     * ========================================================= */
    @Transactional
    public void cancelOrder(Long userId, Long orderId, OrderCancelReq req) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.ORDER_NOT_FOUND));

        boolean isCustomer = order.isPlacedBy(userId);
        boolean isOwner = isStoreOwner(order.getStoreId(), userId);
        if (!isCustomer && !isOwner) {
            throw new BaseException(BaseResponseStatus.ACCESS_DENIED);
        }
        order.cancel(req != null ? req.reason() : null);
        // TODO: 결제 환불 (Payment 도메인 구현 후), 알림 발송
    }

    /* =========================================================
     * 10) 빠른 재주문 — 이전 주문 구성으로 새 PENDING 주문 생성
     * ========================================================= */
    @Transactional
    public OrderCreateRes reorder(Long userId, Long previousOrderId) {
        Order prev = orderRepository.findWithItemsById(previousOrderId)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.ORDER_NOT_FOUND));
        if (!prev.isPlacedBy(userId)) {
            throw new BaseException(BaseResponseStatus.ACCESS_DENIED);
        }
        // 이전 주문의 items를 OrderCreateReq로 변환해서 createOrder 재사용
        List<OrderCreateReq.Item> items = prev.getItems().stream()
                .map(it -> new OrderCreateReq.Item(
                        it.getMenuId(),
                        it.getQuantity(),
                        it.getOptions().stream().map(OrderItemOption::getMenuOptionId).toList()
                ))
                .toList();
        OrderCreateReq req = new OrderCreateReq(
                prev.getStoreId(),
                prev.getOrderType(),
                items
        );
        return createOrder(userId, req);
    }

    /* ============================
     * helpers
     * ============================ */
    private Order findOwnedOrder(Long orderId, Long userId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.ORDER_NOT_FOUND));
        verifyStoreOwner(order.getStoreId(), userId);
        return order;
    }

    private boolean isStoreOwner(Long storeId, Long userId) {
        return storeRepository.findById(storeId)
                .map(s -> s.isOwnedBy(userId))
                .orElse(false);
    }

    private void verifyStoreOwner(Long storeId, Long userId) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.STORE_NOT_FOUND));
        if (!store.isOwnedBy(userId)) {
            throw new BaseException(BaseResponseStatus.NOT_STORE_OWNER);
        }
    }

    /**
     * 주문 목록 → 상세(items·옵션 포함) 응답 리스트.
     * 매장명·메뉴명·옵션정보를 전체 주문에 대해 한 번씩만 조회한다(N+1 방지).
     * items/options 컬렉션은 default_batch_fetch_size로 IN 절 묶음 로딩된다.
     */
    private List<OrderDetailRes> toDetailResponses(List<Order> orders) {
        if (orders.isEmpty()) return List.of();

        Map<Long, String> storeNames = storeNames(orders.stream().map(Order::getStoreId).toList());

        Set<Long> menuIds = orders.stream()
                .flatMap(o -> o.getItems().stream().map(OrderItem::getMenuId))
                .collect(Collectors.toSet());
        Map<Long, String> menuNames = menuIds.isEmpty() ? Map.of()
                : menuRepository.findAllById(menuIds).stream()
                .collect(Collectors.toMap(Menu::getId, Menu::getName));

        Set<Long> optionIds = orders.stream()
                .flatMap(o -> o.getItems().stream())
                .flatMap(it -> it.getOptions().stream())
                .map(OrderItemOption::getMenuOptionId)
                .collect(Collectors.toSet());
        Map<Long, OrderDetailRes.OptionInfo> optionInfos = optionIds.isEmpty() ? Map.of()
                : menuOptionRepository.findAllById(optionIds).stream()
                .collect(Collectors.toMap(
                        MenuOption::getId,
                        o -> new OrderDetailRes.OptionInfo(o.getOptionGroup(), o.getOptionName())));

        return orders.stream()
                .map(o -> OrderDetailRes.of(o,
                        storeNames.getOrDefault(o.getStoreId(), "(삭제된 매장)"),
                        menuNames, optionInfos))
                .toList();
    }

    /** 매장 ID 리스트 → 이름 Map. 삭제된 매장은 null. */
    private Map<Long, String> storeNames(List<Long> storeIds) {
        if (storeIds.isEmpty()) return Map.of();
        return storeRepository.findAllById(new HashSet<>(storeIds)).stream()
                .collect(Collectors.toMap(Store::getId, Store::getName));
    }

    /** 주문 항목 중 '음료' 카테고리의 수량 합 — 스탬프 적립 수량. */
    private int countDrinkStamps(Order order) {
        List<OrderItem> items = order.getItems();
        if (items.isEmpty()) return 0;

        Set<Long> menuIds = items.stream().map(OrderItem::getMenuId).collect(Collectors.toSet());
        // category는 null일 수 있으므로 빈 문자열로 정규화 (toMap은 null value 불가)
        Map<Long, String> categories = menuRepository.findAllById(menuIds).stream()
                .collect(Collectors.toMap(
                        Menu::getId,
                        m -> m.getCategory() == null ? "" : m.getCategory(),
                        (a, b) -> a));

        int total = 0;
        for (OrderItem item : items) {
            if (drinkCategoryPolicy.isDrink(categories.get(item.getMenuId()))) {
                total += item.getQuantity();
            }
        }
        return total;
    }

    /** 주문에 포함된 메뉴들의 이름 Map */
    private Map<Long, String> menuNames(Order order) {
        Set<Long> menuIds = order.getItems().stream()
                .map(OrderItem::getMenuId).collect(Collectors.toSet());
        if (menuIds.isEmpty()) return Map.of();
        return menuRepository.findAllById(menuIds).stream()
                .collect(Collectors.toMap(Menu::getId, Menu::getName));
    }

    /** 주문에 포함된 옵션들의 메타정보 Map */
    private Map<Long, OrderDetailRes.OptionInfo> optionInfos(Order order) {
        Set<Long> optionIds = order.getItems().stream()
                .flatMap(it -> it.getOptions().stream())
                .map(OrderItemOption::getMenuOptionId)
                .collect(Collectors.toSet());
        if (optionIds.isEmpty()) return Map.of();
        return menuOptionRepository.findAllById(optionIds).stream()
                .collect(Collectors.toMap(
                        MenuOption::getId,
                        o -> new OrderDetailRes.OptionInfo(o.getOptionGroup(), o.getOptionName())
                ));
    }
}
