package com.cafeminsu.payment;

import com.cafeminsu.domain.user.entity.User;
import com.cafeminsu.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 카카오페이 결제 흐름 통합 테스트 (KakaoPayClient mock 모드 — secret-key 미설정).
 * prepare → kakaopay/ready → kakaopay/approve → verify(=PAID) 전체 경로를 검증한다.
 */
class KakaoPayFlowTest extends IntegrationTestSupport {

    @Test
    @DisplayName("카카오페이 전체 흐름: ready → approve → verify → PAID")
    void fullKakaoPayFlow() throws Exception {
        Ctx c = prepared();

        // 1) ready
        MvcResult readyRes = mockMvc.perform(post("/api/payments/kakaopay/ready")
                        .header("Authorization", fixtures.authHeader(c.customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"merchantUid\":\"%s\",\"amount\":10000}", c.merchantUid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tid").isString())
                .andExpect(jsonPath("$.redirectUrl").isString())
                .andReturn();
        String tid = objectMapper.readTree(readyRes.getResponse().getContentAsString()).at("/tid").asText();

        // 2) approve
        MvcResult approveRes = mockMvc.perform(post("/api/payments/kakaopay/approve")
                        .header("Authorization", fixtures.authHeader(c.customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"tid\":\"%s\",\"pgToken\":\"pg_ok_001\",\"merchantUid\":\"%s\"}", tid, c.merchantUid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentToken").isString())
                .andReturn();
        String paymentToken = objectMapper.readTree(approveRes.getResponse().getContentAsString())
                .at("/paymentToken").asText();

        // 3) verify — paymentToken을 impUid 슬롯에 넣어 확정
        mockMvc.perform(post("/api/payments/verify")
                        .header("Authorization", fixtures.authHeader(c.customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"impUid\":\"%s\",\"merchantUid\":\"%s\"}", paymentToken, c.merchantUid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    @DisplayName("approve 실패(pgToken=fail*) → 결제 승인 실패")
    void approveFails() throws Exception {
        Ctx c = prepared();
        String tid = ready(c);

        mockMvc.perform(post("/api/payments/kakaopay/approve")
                        .header("Authorization", fixtures.authHeader(c.customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"tid\":\"%s\",\"pgToken\":\"fail_001\",\"merchantUid\":\"%s\"}", tid, c.merchantUid)))
                .andExpect(jsonPath("$.code").value("PAYMENT_VERIFICATION_FAILED"));
    }

    @Test
    @DisplayName("verify에 잘못된 paymentToken을 보내면 확정 실패")
    void verifyWrongToken() throws Exception {
        Ctx c = prepared();
        String tid = ready(c);
        // 정상 approve로 aid 저장
        mockMvc.perform(post("/api/payments/kakaopay/approve")
                        .header("Authorization", fixtures.authHeader(c.customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"tid\":\"%s\",\"pgToken\":\"pg_ok\",\"merchantUid\":\"%s\"}", tid, c.merchantUid)))
                .andExpect(status().isOk());

        // 엉뚱한 토큰으로 verify
        mockMvc.perform(post("/api/payments/verify")
                        .header("Authorization", fixtures.authHeader(c.customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"impUid\":\"WRONG_TOKEN\",\"merchantUid\":\"%s\"}", c.merchantUid)))
                .andExpect(jsonPath("$.code").value("PAYMENT_VERIFICATION_FAILED"));
    }

    @Test
    @DisplayName("ready 금액이 준비된 결제분과 다르면 거부")
    void readyAmountMismatch() throws Exception {
        Ctx c = prepared();

        mockMvc.perform(post("/api/payments/kakaopay/ready")
                        .header("Authorization", fixtures.authHeader(c.customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"merchantUid\":\"%s\",\"amount\":9999}", c.merchantUid)))
                .andExpect(jsonPath("$.code").value("PAYMENT_AMOUNT_MISMATCH"));
    }

    /* ===== helpers ===== */
    record Ctx(User customer, String merchantUid) {}

    /** store/menu/order 생성 후 카드 전액 prepare까지 완료, merchantUid 반환. */
    Ctx prepared() throws Exception {
        User owner = fixtures.createOwner("점주");
        User customer = fixtures.createCustomer("고객");

        MvcResult sRes = mockMvc.perform(post("/api/stores")
                        .header("Authorization", fixtures.authHeader(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"카페\",\"address\":\"인천\",\"latitude\":37.45,\"longitude\":126.73}"))
                .andExpect(status().isOk()).andReturn();
        long storeId = objectMapper.readTree(sRes.getResponse().getContentAsString()).at("/storeId").asLong();

        MvcResult mRes = mockMvc.perform(post("/api/stores/" + storeId + "/menus")
                        .header("Authorization", fixtures.authHeader(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"라떼\",\"price\":5000}"))
                .andExpect(status().isOk()).andReturn();
        long menuId = objectMapper.readTree(mRes.getResponse().getContentAsString()).at("/menuId").asLong();

        MvcResult oRes = mockMvc.perform(post("/api/orders")
                        .header("Authorization", fixtures.authHeader(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"storeId\":%d,\"orderType\":\"MOBILE\",\"items\":[{\"menuId\":%d,\"quantity\":2}]}",
                                storeId, menuId)))
                .andExpect(status().isOk()).andReturn();
        long orderId = objectMapper.readTree(oRes.getResponse().getContentAsString()).at("/orderId").asLong();

        MvcResult pRes = mockMvc.perform(post("/api/payments/prepare")
                        .header("Authorization", fixtures.authHeader(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"orderId\":%d}", orderId)))
                .andExpect(status().isOk()).andReturn();
        String merchantUid = objectMapper.readTree(pRes.getResponse().getContentAsString())
                .at("/merchantUid").asText();

        return new Ctx(customer, merchantUid);
    }

    String ready(Ctx c) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/payments/kakaopay/ready")
                        .header("Authorization", fixtures.authHeader(c.customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"merchantUid\":\"%s\",\"amount\":10000}", c.merchantUid)))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).at("/tid").asText();
    }
}
