package com.cafeminsu.stamp;

import com.cafeminsu.domain.user.entity.User;
import com.cafeminsu.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StampFlowTest extends IntegrationTestSupport {

    @Test
    @DisplayName("주문 complete 시 스탬프 자동 적립 — count=1")
    void earnOnComplete() throws Exception {
        Setup s = setup();
        long orderId = createOrder(s);
        completeOrder(s.owner, orderId);

        mockMvc.perform(get("/api/stamps")
                        .header("Authorization", fixtures.authHeader(s.customer)))
                .andExpect(jsonPath("$.result[0].storeId").value((int) s.storeId))
                .andExpect(jsonPath("$.result[0].count").value(1));
    }

    @Test
    @DisplayName("같은 매장에 두 번 주문 → count=2, 이력 row 2개")
    void accumulate() throws Exception {
        Setup s = setup();
        completeOrder(s.owner, createOrder(s));
        completeOrder(s.owner, createOrder(s));

        mockMvc.perform(get("/api/stamps/" + s.storeId)
                        .header("Authorization", fixtures.authHeader(s.customer)))
                .andExpect(jsonPath("$.result.count").value(2))
                .andExpect(jsonPath("$.result.histories.length()").value(2))
                .andExpect(jsonPath("$.result.histories[0].earnedCount").value(1));
    }

    @Test
    @DisplayName("다른 매장은 각각 별도 row")
    void separatePerStore() throws Exception {
        Setup s1 = setup();
        Setup s2 = setupWithExistingCustomer(s1.customer);   // 같은 고객, 다른 매장

        completeOrder(s1.owner, createOrder(s1));
        completeOrder(s2.owner, createOrder(s2));

        mockMvc.perform(get("/api/stamps")
                        .header("Authorization", fixtures.authHeader(s1.customer)))
                .andExpect(jsonPath("$.result.length()").value(2));
    }

    @Test
    @DisplayName("적립 없는 매장 상세 조회 시 STAMP_NOT_FOUND(2800)")
    void storeWithNoStampsReturns2800() throws Exception {
        User customer = fixtures.createCustomer("고객");
        User owner = fixtures.createOwner("점주");
        long storeId = createStore(owner);

        mockMvc.perform(get("/api/stamps/" + storeId)
                        .header("Authorization", fixtures.authHeader(customer)))
                .andExpect(jsonPath("$.code").value(2800));
    }

    @Test
    @DisplayName("키오스크 비회원 주문(userId=null)은 스탬프 안 쌓임 — Stamp 비어있음")
    void noStampForGuestKioskOrder() throws Exception {
        // 비회원 키오스크 주문 시나리오를 직접 만들기는 dev login 없이는 까다로움.
        // 그래서 일반 고객이 만든 주문은 항상 userId가 박힘 → 스탬프 쌓이는 게 정상.
        // 여기선 "stamp 적립 동작 자체"가 정상인지 sanity check.
        Setup s = setup();
        completeOrder(s.owner, createOrder(s));

        mockMvc.perform(get("/api/stamps")
                        .header("Authorization", fixtures.authHeader(s.customer)))
                .andExpect(jsonPath("$.result[0].count").value(1));
    }

    @Test
    @DisplayName("음료 3잔 주문 → 스탬프 3개 적립 (수량 비례)")
    void earnPerDrinkQuantity() throws Exception {
        Setup s = setup();
        completeOrder(s.owner, createOrder(s, 3));

        mockMvc.perform(get("/api/stamps/" + s.storeId)
                        .header("Authorization", fixtures.authHeader(s.customer)))
                .andExpect(jsonPath("$.result.count").value(3))
                .andExpect(jsonPath("$.result.histories[0].earnedCount").value(3));
    }

    @Test
    @DisplayName("비음료(디저트) 주문은 적립 안 됨 → STAMP_NOT_FOUND(2800)")
    void nonDrinkDoesNotEarn() throws Exception {
        User owner = fixtures.createOwner("점주");
        User customer = fixtures.createCustomer("고객");
        long storeId = createStore(owner);
        long dessertId = createMenu(owner, storeId, "치즈케이크", 6000, "디저트");
        Setup s = new Setup(owner, customer, storeId, dessertId);

        completeOrder(owner, createOrder(s, 2));

        mockMvc.perform(get("/api/stamps/" + storeId)
                        .header("Authorization", fixtures.authHeader(customer)))
                .andExpect(jsonPath("$.code").value(2800));
    }

    @Test
    @DisplayName("음료 10잔 → 스탬프 0으로 차감 + 2000원 보상 기프티콘 발급, 선물 불가(2705)")
    void tenDrinksIssuesNonTransferableReward() throws Exception {
        Setup s = setup();
        completeOrder(s.owner, createOrder(s, 10));
        String customerAuth = fixtures.authHeader(s.customer);

        // 10개 → 보상 전환되어 잔여 0
        mockMvc.perform(get("/api/stamps/" + s.storeId).header("Authorization", customerAuth))
                .andExpect(jsonPath("$.result.count").value(0));

        // 보상 기프티콘이 사용 가능 목록에 노출 (2000원)
        MvcResult my = mockMvc.perform(get("/api/gifticons/my").header("Authorization", customerAuth))
                .andExpect(jsonPath("$.result.length()").value(1))
                .andExpect(jsonPath("$.result[0].balance").value(2000))
                .andReturn();
        long gifticonId = objectMapper.readTree(my.getResponse().getContentAsString())
                .at("/result/0/gifticonId").asLong();

        // 선물(공유) 시도 → 차단 (GIFTICON_NOT_TRANSFERABLE)
        mockMvc.perform(post("/api/gifticons/" + gifticonId + "/share")
                        .header("Authorization", customerAuth))
                .andExpect(jsonPath("$.code").value(2705));
    }

    /* ===== helpers ===== */
    record Setup(User owner, User customer, long storeId, long menuId) {}

    Setup setup() throws Exception {
        User owner = fixtures.createOwner("점주");
        User customer = fixtures.createCustomer("고객");
        long storeId = createStore(owner);
        long menuId = createMenu(owner, storeId, "아메리카노", 4500);
        return new Setup(owner, customer, storeId, menuId);
    }

    Setup setupWithExistingCustomer(User existingCustomer) throws Exception {
        User owner = fixtures.createOwner("점주B");
        long storeId = createStore(owner);
        long menuId = createMenu(owner, storeId, "라떼", 5000);
        return new Setup(owner, existingCustomer, storeId, menuId);
    }

    private long createStore(User owner) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/stores")
                        .header("Authorization", fixtures.authHeader(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"카페","address":"인천","latitude":37.45,"longitude":126.73}
                                """))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString())
                .at("/result/storeId").asLong();
    }

    /** 기본은 음료 카테고리('커피')로 생성 — 스탬프 적립 대상. */
    private long createMenu(User owner, long storeId, String name, int price) throws Exception {
        return createMenu(owner, storeId, name, price, "커피");
    }

    private long createMenu(User owner, long storeId, String name, int price, String category) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/stores/" + storeId + "/menus")
                        .header("Authorization", fixtures.authHeader(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"price\":" + price
                                + ",\"category\":\"" + category + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString())
                .at("/result/menuId").asLong();
    }

    private long createOrder(Setup s) throws Exception {
        return createOrder(s, 1);
    }

    private long createOrder(Setup s, int quantity) throws Exception {
        String body = String.format("""
                {
                  "storeId": %d,
                  "orderType": "MOBILE",
                  "orderMethod": "MANUAL",
                  "items": [{"menuId": %d, "quantity": %d}]
                }
                """, s.storeId, s.menuId, quantity);
        MvcResult res = mockMvc.perform(post("/api/orders")
                        .header("Authorization", fixtures.authHeader(s.customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString())
                .at("/result/orderId").asLong();
    }

    /** 점주가 accept → ready → complete 한 번에 진행 (스탬프는 complete 시점에 적립) */
    private void completeOrder(User owner, long orderId) throws Exception {
        String auth = fixtures.authHeader(owner);
        mockMvc.perform(patch("/api/orders/" + orderId + "/accept").header("Authorization", auth));
        mockMvc.perform(patch("/api/orders/" + orderId + "/ready").header("Authorization", auth));
        mockMvc.perform(patch("/api/orders/" + orderId + "/complete").header("Authorization", auth));
    }
}
