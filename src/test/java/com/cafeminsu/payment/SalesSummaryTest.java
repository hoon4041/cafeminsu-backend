package com.cafeminsu.payment;

import com.cafeminsu.domain.user.entity.User;
import com.cafeminsu.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * GET /api/stores/{storeId}/sales-summary 통합 테스트.
 * 결제 완료(PAID)된 주문이 총매출/일자별/메뉴별 집계에 반영되는지 + 인가를 검증한다.
 */
class SalesSummaryTest extends IntegrationTestSupport {

    @Test
    @DisplayName("PAID 결제가 총매출·일자별·메뉴별 집계에 반영된다")
    void summaryAggregatesPaidPayment() throws Exception {
        Fixture f = paidOrderFixture();   // 라떼 5000 × 2 = 10000, 카드 결제 PAID

        mockMvc.perform(get("/api/stores/" + f.storeId + "/sales-summary")
                        .header("Authorization", fixtures.authHeader(f.owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSales").value(10000))
                // 오늘 날짜 버킷 1건
                .andExpect(jsonPath("$.dailySales.length()").value(1))
                .andExpect(jsonPath("$.dailySales[0].date").value(LocalDate.now().toString()))
                .andExpect(jsonPath("$.dailySales[0].amount").value(10000))
                .andExpect(jsonPath("$.dailySales[0].orderCount").value(1))
                // 메뉴별 — 라떼 2잔, 단가×수량 10000
                .andExpect(jsonPath("$.topMenus.length()").value(1))
                .andExpect(jsonPath("$.topMenus[0].menuId").value(f.menuId))
                .andExpect(jsonPath("$.topMenus[0].name").value("라떼"))
                .andExpect(jsonPath("$.topMenus[0].quantity").value(2))
                .andExpect(jsonPath("$.topMenus[0].amount").value(10000));
    }

    @Test
    @DisplayName("결제 없는 매장은 빈 집계(0/빈 배열)를 반환한다")
    void emptySummary() throws Exception {
        User owner = fixtures.createOwner("점주");
        long storeId = createStore(owner);

        mockMvc.perform(get("/api/stores/" + storeId + "/sales-summary")
                        .header("Authorization", fixtures.authHeader(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSales").value(0))
                .andExpect(jsonPath("$.dailySales.length()").value(0))
                .andExpect(jsonPath("$.topMenus.length()").value(0));
    }

    @Test
    @DisplayName("다른 점주는 남의 매장 매출을 조회할 수 없다")
    void otherOwnerForbidden() throws Exception {
        Fixture f = paidOrderFixture();
        User intruder = fixtures.createOwner("다른점주");

        mockMvc.perform(get("/api/stores/" + f.storeId + "/sales-summary")
                        .header("Authorization", fixtures.authHeader(intruder)))
                .andExpect(jsonPath("$.code").value("NOT_STORE_OWNER"));
    }

    @Test
    @DisplayName("일반 고객(USER)은 매출 요약에 접근할 수 없다 (403)")
    void customerForbidden() throws Exception {
        Fixture f = paidOrderFixture();

        mockMvc.perform(get("/api/stores/" + f.storeId + "/sales-summary")
                        .header("Authorization", fixtures.authHeader(f.customer)))
                .andExpect(status().isForbidden());
    }

    /* ===== helpers ===== */
    record Fixture(User owner, User customer, long storeId, long menuId, long orderId) {}

    /** 주문 생성 → 카드 전액 결제 → verify(PAID)까지 완료한 픽스처. */
    Fixture paidOrderFixture() throws Exception {
        User owner = fixtures.createOwner("점주");
        User customer = fixtures.createCustomer("고객");
        long storeId = createStore(owner);
        long menuId = createMenu(owner, storeId);
        long orderId = createOrder(customer, storeId, menuId);

        // prepare
        MvcResult prepareRes = mockMvc.perform(post("/api/payments/prepare")
                        .header("Authorization", fixtures.authHeader(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"orderId\":%d,\"cardAmount\":10000}", orderId)))
                .andExpect(status().isOk()).andReturn();
        String merchantUid = objectMapper.readTree(prepareRes.getResponse().getContentAsString())
                .at("/merchantUid").asText();

        // verify → PAID
        mockMvc.perform(post("/api/payments/verify")
                        .header("Authorization", fixtures.authHeader(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"impUid\":\"imp_test_sales\",\"merchantUid\":\"%s\"}", merchantUid)))
                .andExpect(jsonPath("$.status").value("PAID"));

        return new Fixture(owner, customer, storeId, menuId, orderId);
    }

    long createStore(User owner) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/stores")
                        .header("Authorization", fixtures.authHeader(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"카페","address":"인천","latitude":37.45,"longitude":126.73}
                                """))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).at("/storeId").asLong();
    }

    long createMenu(User owner, long storeId) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/stores/" + storeId + "/menus")
                        .header("Authorization", fixtures.authHeader(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"라떼\",\"price\":5000}"))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).at("/menuId").asLong();
    }

    long createOrder(User customer, long storeId, long menuId) throws Exception {
        String body = String.format("""
                {"storeId": %d, "orderType": "MOBILE", "items": [{"menuId": %d, "quantity": 2}]}
                """, storeId, menuId);
        MvcResult res = mockMvc.perform(post("/api/orders")
                        .header("Authorization", fixtures.authHeader(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).at("/orderId").asLong();
    }
}
