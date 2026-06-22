package com.cafeminsu.order;

import com.cafeminsu.domain.user.entity.User;
import com.cafeminsu.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OrderFlowTest extends IntegrationTestSupport {

    @Test
    @DisplayName("주문 생성 — 가격은 서버 DB에서 재계산 (클라가 price 보내도 무시)")
    void priceRecalculatedByServer() throws Exception {
        TestContext ctx = setupStoreAndMenu();

        // 클라가 의도적으로 price 필드를 추가로 보내도 OrderCreateReq에 없으니 무시됨
        String body = """
                {
                  "storeId": %d,
                  "orderType": "MOBILE",
                  "orderMethod": "MANUAL",
                  "items": [
                    {"menuId": %d, "quantity": 2, "optionIds": [%d], "price": 0}
                  ]
                }
                """.formatted(ctx.storeId, ctx.menuId, ctx.optionId);

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", fixtures.authHeader(ctx.customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmount").value(10000))   // (4500+500)*2
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.orderNumber").isString());
    }

    @Test
    @DisplayName("다른 매장 메뉴를 끼워넣으면 거절")
    void differentStoreMenuRejected() throws Exception {
        TestContext ctx = setupStoreAndMenu();

        // 두 번째 매장과 메뉴
        User otherOwner = fixtures.createOwner("점주2");
        long otherStoreId = createStore(otherOwner);
        long otherMenuId = createMenu(otherOwner, otherStoreId, "라떼", 5000);

        // 첫 번째 매장에 두 번째 매장의 메뉴를 끼워넣기 시도
        String body = """
                {
                  "storeId": %d,
                  "orderType": "MOBILE",
                  "orderMethod": "MANUAL",
                  "items": [{"menuId": %d, "quantity": 1}]
                }
                """.formatted(ctx.storeId, otherMenuId);

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", fixtures.authHeader(ctx.customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(jsonPath("$.code").value("MENU_NOT_FOUND"));
    }

    @Test
    @DisplayName("상태 전이: PENDING → ACCEPTED → READY → DONE")
    void stateTransition() throws Exception {
        TestContext ctx = setupStoreAndMenu();
        long orderId = createOrder(ctx);

        mockMvc.perform(patch("/api/orders/" + orderId + "/accept")
                        .header("Authorization", fixtures.authHeader(ctx.owner)))
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        mockMvc.perform(patch("/api/orders/" + orderId + "/ready")
                        .header("Authorization", fixtures.authHeader(ctx.owner)))
                .andExpect(jsonPath("$.status").value("READY"));

        mockMvc.perform(patch("/api/orders/" + orderId + "/complete")
                        .header("Authorization", fixtures.authHeader(ctx.owner)))
                .andExpect(jsonPath("$.status").value("DONE"));
    }

    @Test
    @DisplayName("잘못된 상태 전이 — 이미 DONE인 주문을 다시 accept 시 INVALID_ORDER_STATUS")
    void invalidStateTransition() throws Exception {
        TestContext ctx = setupStoreAndMenu();
        long orderId = createOrder(ctx);

        // accept → ready → complete (DONE)
        mockMvc.perform(patch("/api/orders/" + orderId + "/accept").header("Authorization", fixtures.authHeader(ctx.owner)));
        mockMvc.perform(patch("/api/orders/" + orderId + "/ready").header("Authorization", fixtures.authHeader(ctx.owner)));
        mockMvc.perform(patch("/api/orders/" + orderId + "/complete").header("Authorization", fixtures.authHeader(ctx.owner)));

        // DONE 상태에서 다시 accept 시도
        mockMvc.perform(patch("/api/orders/" + orderId + "/accept")
                        .header("Authorization", fixtures.authHeader(ctx.owner)))
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_STATUS"));
    }

    @Test
    @DisplayName("다른 점주가 남의 매장 주문에 손대면 거절")
    void otherOwnerCannotAccept() throws Exception {
        TestContext ctx = setupStoreAndMenu();
        long orderId = createOrder(ctx);
        User otherOwner = fixtures.createOwner("다른점주");

        mockMvc.perform(patch("/api/orders/" + orderId + "/accept")
                        .header("Authorization", fixtures.authHeader(otherOwner)))
                .andExpect(jsonPath("$.code").value("NOT_STORE_OWNER"));
    }

    @Test
    @DisplayName("주문 상세 — 본인 주문은 조회 가능")
    void customerCanViewOwnOrder() throws Exception {
        TestContext ctx = setupStoreAndMenu();
        long orderId = createOrder(ctx);

        mockMvc.perform(get("/api/orders/" + orderId)
                        .header("Authorization", fixtures.authHeader(ctx.customer)))
                .andExpect(jsonPath("$.orderId").value((int) orderId))
                .andExpect(jsonPath("$.totalAmount").value(10000));
    }

    @Test
    @DisplayName("주문 상세 — 다른 고객은 남의 주문 조회 불가")
    void otherCustomerCannotViewOrder() throws Exception {
        TestContext ctx = setupStoreAndMenu();
        long orderId = createOrder(ctx);
        User other = fixtures.createCustomer("타인");

        mockMvc.perform(get("/api/orders/" + orderId)
                        .header("Authorization", fixtures.authHeader(other)))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("재주문 — 이전 주문 구성으로 새 PENDING 주문 생성")
    void reorder() throws Exception {
        TestContext ctx = setupStoreAndMenu();
        long previousOrderId = createOrder(ctx);

        mockMvc.perform(post("/api/orders/reorder/" + previousOrderId)
                        .header("Authorization", fixtures.authHeader(ctx.customer)))
                .andExpect(jsonPath("$.totalAmount").value(10000))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    /* ===== helpers ===== */
    record TestContext(User owner, User customer, long storeId, long menuId, long optionId) {}

    TestContext setupStoreAndMenu() throws Exception {
        User owner = fixtures.createOwner("점주");
        User customer = fixtures.createCustomer("고객");
        long storeId = createStore(owner);
        long menuId = createMenu(owner, storeId, "아메리카노", 4500);
        long optionId = addOption(owner, menuId, "size", "L", 500);
        return new TestContext(owner, customer, storeId, menuId, optionId);
    }

    private long createStore(User owner) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/stores")
                        .header("Authorization", fixtures.authHeader(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"카페","address":"인천","latitude":37.45,"longitude":126.73}
                                """))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).at("/storeId").asLong();
    }

    private long createMenu(User owner, long storeId, String name, int price) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/stores/" + storeId + "/menus")
                        .header("Authorization", fixtures.authHeader(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"price\":" + price + "}"))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).at("/menuId").asLong();
    }

    private long addOption(User owner, long menuId, String group, String name, int addPrice) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/menus/" + menuId + "/options")
                        .header("Authorization", fixtures.authHeader(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"optionGroup\":\"" + group + "\",\"optionName\":\"" + name
                                + "\",\"additionalPrice\":" + addPrice + "}"))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).at("/optionId").asLong();
    }

    private long createOrder(TestContext ctx) throws Exception {
        String body = """
                {
                  "storeId": %d,
                  "orderType": "MOBILE",
                  "orderMethod": "MANUAL",
                  "items": [{"menuId": %d, "quantity": 2, "optionIds": [%d]}]
                }
                """.formatted(ctx.storeId, ctx.menuId, ctx.optionId);
        MvcResult res = mockMvc.perform(post("/api/orders")
                        .header("Authorization", fixtures.authHeader(ctx.customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).at("/orderId").asLong();
    }
}
