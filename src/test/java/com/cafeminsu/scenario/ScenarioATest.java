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
 * 시나리오 A: 매장 → 메뉴 → 주문 → 결제 → 상태 전이 전체 흐름.
 * 도메인이 서로 잘 연동되는지 확인.
 */
class ScenarioATest extends IntegrationTestSupport {

    @Test
    @DisplayName("E2E: 점주가 매장·메뉴 세팅 → 고객이 주문·결제 → 점주가 픽업 완료까지")
    void fullFlow() throws Exception {
        User owner = fixtures.createOwner("점주");
        User customer = fixtures.createCustomer("고객");

        /* === 1. 매장 등록 === */
        MvcResult storeRes = mockMvc.perform(post("/api/stores")
                        .header("Authorization", fixtures.authHeader(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "민수카페 인천점",
                                  "address": "인천 남동구 구월동",
                                  "latitude": 37.4503,
                                  "longitude": 126.7314
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        long storeId = objectMapper.readTree(storeRes.getResponse().getContentAsString())
                .at("/storeId").asLong();

        /* === 2. 메뉴 + 옵션 === */
        MvcResult menuRes = mockMvc.perform(post("/api/stores/" + storeId + "/menus")
                        .header("Authorization", fixtures.authHeader(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"아메리카노\",\"price\":4500,\"category\":\"커피\"}"))
                .andExpect(status().isOk())
                .andReturn();
        long menuId = objectMapper.readTree(menuRes.getResponse().getContentAsString())
                .at("/menuId").asLong();

        MvcResult optRes = mockMvc.perform(post("/api/menus/" + menuId + "/options")
                        .header("Authorization", fixtures.authHeader(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"optionGroup\":\"size\",\"optionName\":\"L\",\"additionalPrice\":500}"))
                .andExpect(status().isOk())
                .andReturn();
        long optionId = objectMapper.readTree(optRes.getResponse().getContentAsString())
                .at("/optionId").asLong();

        /* === 3. 고객이 메뉴 둘러보기 (비로그인 OK) === */
        mockMvc.perform(get("/api/stores/" + storeId + "/menus"))
                .andExpect(jsonPath("$[0].name").value("아메리카노"));

        /* === 4. 주문 생성 === */
        String orderBody = String.format("""
                {
                  "storeId": %d,
                  "orderType": "MOBILE",
                  "orderMethod": "MANUAL",
                  "items": [{"menuId": %d, "quantity": 2, "optionIds": [%d]}]
                }
                """, storeId, menuId, optionId);
        MvcResult orderRes = mockMvc.perform(post("/api/orders")
                        .header("Authorization", fixtures.authHeader(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmount").value(10000))   // (4500+500)*2
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        long orderId = objectMapper.readTree(orderRes.getResponse().getContentAsString())
                .at("/orderId").asLong();

        /* === 5. 결제 — prepare → verify === */
        MvcResult prepareRes = mockMvc.perform(post("/api/payments/prepare")
                        .header("Authorization", fixtures.authHeader(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"orderId\":%d,\"cardAmount\":10000}", orderId)))
                .andExpect(status().isOk())
                .andReturn();
        String merchantUid = objectMapper.readTree(prepareRes.getResponse().getContentAsString())
                .at("/merchantUid").asText();

        mockMvc.perform(post("/api/payments/verify")
                        .header("Authorization", fixtures.authHeader(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"impUid\":\"imp_test_e2e\",\"merchantUid\":\"%s\"}", merchantUid)))
                .andExpect(jsonPath("$.status").value("PAID"));

        /* === 6. 점주가 매장 주문 목록 확인 === */
        mockMvc.perform(get("/api/stores/" + storeId + "/orders")
                        .header("Authorization", fixtures.authHeader(owner)))
                .andExpect(jsonPath("$[0].orderId").value((int) orderId))
                .andExpect(jsonPath("$[0].status").value("PENDING"));

        /* === 7. 점주 상태 전이: 수락 → 준비완료 → 픽업완료 === */
        mockMvc.perform(patch("/api/orders/" + orderId + "/accept")
                        .header("Authorization", fixtures.authHeader(owner)))
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        mockMvc.perform(patch("/api/orders/" + orderId + "/ready")
                        .header("Authorization", fixtures.authHeader(owner)))
                .andExpect(jsonPath("$.status").value("READY"));

        mockMvc.perform(patch("/api/orders/" + orderId + "/complete")
                        .header("Authorization", fixtures.authHeader(owner)))
                .andExpect(jsonPath("$.status").value("DONE"));

        /* === 8. 매장 정산 확인 === */
        mockMvc.perform(get("/api/stores/" + storeId + "/payments")
                        .header("Authorization", fixtures.authHeader(owner)))
                .andExpect(jsonPath("$.total").value(10000));
    }
}
