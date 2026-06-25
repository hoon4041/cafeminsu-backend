package com.cafeminsu.scenario;

import com.cafeminsu.domain.user.entity.User;
import com.cafeminsu.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 시나리오 B: 주문 → 결제 → 상태 전이 → 알림 + 스탬프 자동 발생까지 한 흐름.
 * Order와 Notification·Stamp 도메인이 잘 연동되는지 검증.
 */
class ScenarioBTest extends IntegrationTestSupport {

    @Test
    @DisplayName("E2E: 결제 완료 → 점주 상태 전이 → 고객에게 알림 2건 + 스탬프 2개(음료 2잔)")
    void fullFlowWithStampAndNotification() throws Exception {
        User owner = fixtures.createOwner("점주");
        User customer = fixtures.createCustomer("고객");

        /* === 매장 + 메뉴 === */
        long storeId = createStore(owner);
        long menuId = createMenu(owner, storeId, "아메리카노", 4500);

        /* === 주문 생성 + 결제 === */
        long orderId = createOrder(customer, storeId, menuId, 2);
        String merchantUid = prepare(customer, orderId);
        payByKakao(customer, merchantUid, 9000);

        /* === 점주 accept → ready → complete === */
        String ownerAuth = fixtures.authHeader(owner);
        mockMvc.perform(patch("/api/orders/" + orderId + "/accept").header("Authorization", ownerAuth));
        mockMvc.perform(patch("/api/orders/" + orderId + "/ready").header("Authorization", ownerAuth));
        mockMvc.perform(patch("/api/orders/" + orderId + "/complete").header("Authorization", ownerAuth));

        /* === 알림: accept + ready 두 번 === */
        String customerAuth = fixtures.authHeader(customer);
        mockMvc.perform(get("/api/notifications").header("Authorization", customerAuth))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].relatedEntityId").value(
                        org.hamcrest.Matchers.everyItem(
                                org.hamcrest.Matchers.equalTo((int) orderId))));

        mockMvc.perform(get("/api/notifications/unread-count").header("Authorization", customerAuth))
                .andExpect(jsonPath("$.count").value(2));

        /* === 스탬프: 음료 2잔 → 2개 적립 (적립 1회 이력) === */
        mockMvc.perform(get("/api/stamps").header("Authorization", customerAuth))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].storeId").value((int) storeId))
                .andExpect(jsonPath("$[0].count").value(2));

        mockMvc.perform(get("/api/stamps/" + storeId).header("Authorization", customerAuth))
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.histories.length()").value(1))
                .andExpect(jsonPath("$.histories[0].earnedCount").value(2));

        /* === 정산 (점주) === */
        mockMvc.perform(get("/api/stores/" + storeId + "/payments").header("Authorization", ownerAuth))
                .andExpect(jsonPath("$.total").value(9000));
    }

    /* ===== helpers ===== */
    private long createStore(User owner) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/stores")
                        .header("Authorization", fixtures.authHeader(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"카페","address":"인천","latitude":37.45,"longitude":126.73}
                                """))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString())
                .at("/storeId").asLong();
    }

    private long createMenu(User owner, long storeId, String name, int price) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/stores/" + storeId + "/menus")
                        .header("Authorization", fixtures.authHeader(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"price\":" + price + ",\"category\":\"커피\"}"))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString())
                .at("/menuId").asLong();
    }

    private long createOrder(User customer, long storeId, long menuId, int quantity) throws Exception {
        String body = String.format("""
                {
                  "storeId": %d,
                  "orderType": "MOBILE",
                  "items": [{"menuId": %d, "quantity": %d}]
                }
                """, storeId, menuId, quantity);
        MvcResult res = mockMvc.perform(post("/api/orders")
                        .header("Authorization", fixtures.authHeader(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString())
                .at("/orderId").asLong();
    }

    private String prepare(User customer, long orderId) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/payments/prepare")
                        .header("Authorization", fixtures.authHeader(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"orderId\":%d}", orderId)))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString())
                .at("/merchantUid").asText();
    }

    /** 카카오페이 ready → approve → verify(PAID)까지 수행. */
    private void payByKakao(User customer, String merchantUid, int amount) throws Exception {
        String auth = fixtures.authHeader(customer);
        MvcResult readyRes = mockMvc.perform(post("/api/payments/kakaopay/ready")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"merchantUid\":\"%s\",\"amount\":%d}", merchantUid, amount)))
                .andExpect(status().isOk()).andReturn();
        String tid = objectMapper.readTree(readyRes.getResponse().getContentAsString()).at("/tid").asText();

        MvcResult approveRes = mockMvc.perform(post("/api/payments/kakaopay/approve")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"tid\":\"%s\",\"pgToken\":\"pg_ok\",\"merchantUid\":\"%s\"}", tid, merchantUid)))
                .andExpect(status().isOk()).andReturn();
        String paymentToken = objectMapper.readTree(approveRes.getResponse().getContentAsString())
                .at("/paymentToken").asText();

        mockMvc.perform(post("/api/payments/verify")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"impUid\":\"%s\",\"merchantUid\":\"%s\"}", paymentToken, merchantUid)))
                .andExpect(jsonPath("$.status").value("PAID"));
    }
}
