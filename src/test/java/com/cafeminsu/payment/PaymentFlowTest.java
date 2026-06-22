package com.cafeminsu.payment;

import com.cafeminsu.domain.user.entity.User;
import com.cafeminsu.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PaymentFlowTest extends IntegrationTestSupport {

    @Test
    @DisplayName("단일 카드 결제: prepare → verify → PAID")
    void singleCardPayment() throws Exception {
        Setup s = setup();

        // 1) prepare
        MvcResult prepareRes = mockMvc.perform(post("/api/payments/prepare")
                        .header("Authorization", fixtures.authHeader(s.customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"orderId\":%d,\"cardAmount\":10000}", s.orderId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchantUid").isString())
                .andExpect(jsonPath("$.amount").value(10000))
                .andReturn();
        String merchantUid = objectMapper.readTree(prepareRes.getResponse().getContentAsString())
                .at("/merchantUid").asText();

        // 2) verify
        mockMvc.perform(post("/api/payments/verify")
                        .header("Authorization", fixtures.authHeader(s.customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"impUid\":\"imp_test_001\",\"merchantUid\":\"%s\"}", merchantUid)))
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    @DisplayName("분할결제 — 기프티콘 + 카드, 합계 일치")
    void splitPayment() throws Exception {
        Setup s = setup();

        mockMvc.perform(post("/api/payments/prepare")
                        .header("Authorization", fixtures.authHeader(s.customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"orderId\":%d,\"useGifticonId\":999,\"gifticonAmount\":3000,\"cardAmount\":7000}",
                                s.orderId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(7000));   // card amount만 응답
    }

    @Test
    @DisplayName("분할결제 합계 불일치 시 거부 (2603)")
    void splitPaymentAmountMismatch() throws Exception {
        Setup s = setup();

        // totalAmount는 10000인데 합계 9000
        mockMvc.perform(post("/api/payments/prepare")
                        .header("Authorization", fixtures.authHeader(s.customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"orderId\":%d,\"useGifticonId\":999,\"gifticonAmount\":3000,\"cardAmount\":6000}",
                                s.orderId)))
                .andExpect(jsonPath("$.code").value("SPLIT_PAYMENT_AMOUNT_INVALID"));
    }

    @Test
    @DisplayName("같은 주문에 prepare 두 번 호출 시 거부")
    void duplicatePrepareRejected() throws Exception {
        Setup s = setup();
        prepare(s, 10000);

        // 두 번째 prepare
        mockMvc.perform(post("/api/payments/prepare")
                        .header("Authorization", fixtures.authHeader(s.customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"orderId\":%d,\"cardAmount\":10000}", s.orderId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_STATUS"));
    }

    @Test
    @DisplayName("imp_fail로 시작하는 impUid → verify 실패 (mock 동작 검증)")
    void verifyFailsForFailedImpUid() throws Exception {
        Setup s = setup();
        String merchantUid = prepare(s, 10000);

        mockMvc.perform(post("/api/payments/verify")
                        .header("Authorization", fixtures.authHeader(s.customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"impUid\":\"imp_fail_001\",\"merchantUid\":\"%s\"}", merchantUid)))
                .andExpect(jsonPath("$.code").value("PAYMENT_AMOUNT_MISMATCH"));
    }

    @Test
    @DisplayName("남의 주문에 결제 시도 시 거부")
    void otherCustomerCannotPay() throws Exception {
        Setup s = setup();
        User intruder = fixtures.createCustomer("훔치는사람");

        mockMvc.perform(post("/api/payments/prepare")
                        .header("Authorization", fixtures.authHeader(intruder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"orderId\":%d,\"cardAmount\":10000}", s.orderId)))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    /* ===== helpers ===== */
    record Setup(User owner, User customer, long storeId, long menuId, long orderId) {}

    Setup setup() throws Exception {
        User owner = fixtures.createOwner("점주");
        User customer = fixtures.createCustomer("고객");

        // store
        MvcResult sRes = mockMvc.perform(post("/api/stores")
                        .header("Authorization", fixtures.authHeader(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"카페","address":"인천","latitude":37.45,"longitude":126.73}
                                """))
                .andExpect(status().isOk()).andReturn();
        long storeId = objectMapper.readTree(sRes.getResponse().getContentAsString())
                .at("/storeId").asLong();

        // menu (price 5000)
        MvcResult mRes = mockMvc.perform(post("/api/stores/" + storeId + "/menus")
                        .header("Authorization", fixtures.authHeader(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"라떼\",\"price\":5000}"))
                .andExpect(status().isOk()).andReturn();
        long menuId = objectMapper.readTree(mRes.getResponse().getContentAsString())
                .at("/menuId").asLong();

        // order (quantity 2 → total 10000)
        String orderBody = String.format("""
                {
                  "storeId": %d,
                  "orderType": "MOBILE",
                  "orderMethod": "MANUAL",
                  "items": [{"menuId": %d, "quantity": 2}]
                }
                """, storeId, menuId);
        MvcResult oRes = mockMvc.perform(post("/api/orders")
                        .header("Authorization", fixtures.authHeader(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody))
                .andExpect(status().isOk()).andReturn();
        long orderId = objectMapper.readTree(oRes.getResponse().getContentAsString())
                .at("/orderId").asLong();

        return new Setup(owner, customer, storeId, menuId, orderId);
    }

    String prepare(Setup s, int cardAmount) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/payments/prepare")
                        .header("Authorization", fixtures.authHeader(s.customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"orderId\":%d,\"cardAmount\":%d}", s.orderId, cardAmount)))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString())
                .at("/merchantUid").asText();
    }
}
