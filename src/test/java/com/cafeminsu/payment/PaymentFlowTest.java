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

        // 2) 카카오페이 ready → approve → verify
        payByKakao(s.customer, merchantUid, 10000);
    }

    @Test
    @DisplayName("분할결제 — 기프티콘 + 카드, verify 시 기프티콘 잔액 차감")
    void splitPayment() throws Exception {
        Setup s = setup();
        long gifticonId = createGifticon(s.customer, 3000);   // 본인 귀속 기프티콘

        // 1) prepare — 기프티콘 3000 + 카드 7000
        MvcResult prepareRes = mockMvc.perform(post("/api/payments/prepare")
                        .header("Authorization", fixtures.authHeader(s.customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"orderId\":%d,\"useGifticonId\":%d,\"gifticonAmount\":3000,\"cardAmount\":7000}",
                                s.orderId, gifticonId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(7000))   // card amount만 응답
                .andReturn();
        String merchantUid = objectMapper.readTree(prepareRes.getResponse().getContentAsString())
                .at("/merchantUid").asText();

        // 2) 카드 결제분 카카오페이 승인 + verify → 기프티콘도 함께 차감
        payByKakao(s.customer, merchantUid, 7000);

        // 3) 기프티콘 잔액이 3000 → 0으로 차감되었는지 확인
        assertGifticonBalance(s.customer, gifticonId, 0);
    }

    @Test
    @DisplayName("전액 기프티콘 결제 — 카드 없이 prepare → verify로 확정·차감")
    void fullGifticonPayment() throws Exception {
        Setup s = setup();
        long gifticonId = createGifticon(s.customer, 10000);

        // 1) prepare — 전액 기프티콘(cardAmount 0)
        MvcResult prepareRes = mockMvc.perform(post("/api/payments/prepare")
                        .header("Authorization", fixtures.authHeader(s.customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"orderId\":%d,\"useGifticonId\":%d,\"gifticonAmount\":10000,\"cardAmount\":0}",
                                s.orderId, gifticonId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(0))
                .andReturn();
        String merchantUid = objectMapper.readTree(prepareRes.getResponse().getContentAsString())
                .at("/merchantUid").asText();

        // 2) 카카오페이 없이 바로 verify → PAID
        mockMvc.perform(post("/api/payments/verify")
                        .header("Authorization", fixtures.authHeader(s.customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"impUid\":\"n/a\",\"merchantUid\":\"%s\"}", merchantUid)))
                .andExpect(jsonPath("$.status").value("PAID"));

        // 3) 잔액 차감 확인
        assertGifticonBalance(s.customer, gifticonId, 0);
    }

    @Test
    @DisplayName("분할결제 합계 불일치 시 거부 (2603)")
    void splitPaymentAmountMismatch() throws Exception {
        Setup s = setup();
        long gifticonId = createGifticon(s.customer, 3000);

        // totalAmount는 10000인데 합계 9000
        mockMvc.perform(post("/api/payments/prepare")
                        .header("Authorization", fixtures.authHeader(s.customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"orderId\":%d,\"useGifticonId\":%d,\"gifticonAmount\":3000,\"cardAmount\":6000}",
                                s.orderId, gifticonId)))
                .andExpect(jsonPath("$.code").value("SPLIT_PAYMENT_AMOUNT_INVALID"));
    }

    @Test
    @DisplayName("기프티콘 잔액보다 큰 금액으로 결제 시 거부 (2703)")
    void gifticonInsufficientBalance() throws Exception {
        Setup s = setup();
        long gifticonId = createGifticon(s.customer, 2000);   // 잔액 2000

        // 합계는 10000으로 맞지만 기프티콘 잔액(2000) < 사용액(3000)
        mockMvc.perform(post("/api/payments/prepare")
                        .header("Authorization", fixtures.authHeader(s.customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"orderId\":%d,\"useGifticonId\":%d,\"gifticonAmount\":3000,\"cardAmount\":7000}",
                                s.orderId, gifticonId)))
                .andExpect(jsonPath("$.code").value("GIFTICON_INSUFFICIENT_BALANCE"));
    }

    @Test
    @DisplayName("남의 기프티콘으로 결제 시도 시 거부")
    void cannotUseOthersGifticon() throws Exception {
        Setup s = setup();
        User other = fixtures.createCustomer("남");
        long gifticonId = createGifticon(other, 3000);   // 남의 기프티콘

        mockMvc.perform(post("/api/payments/prepare")
                        .header("Authorization", fixtures.authHeader(s.customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"orderId\":%d,\"useGifticonId\":%d,\"gifticonAmount\":3000,\"cardAmount\":7000}",
                                s.orderId, gifticonId)))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
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
    @DisplayName("카카오페이 ready를 거치지 않은 결제는 verify 실패")
    void verifyFailsWithoutKakaoPay() throws Exception {
        Setup s = setup();
        String merchantUid = prepare(s, 10000);

        // ready/approve 없이 바로 verify → 카카오페이로 준비되지 않은 결제이므로 실패
        mockMvc.perform(post("/api/payments/verify")
                        .header("Authorization", fixtures.authHeader(s.customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"impUid\":\"any_token\",\"merchantUid\":\"%s\"}", merchantUid)))
                .andExpect(jsonPath("$.code").value("PAYMENT_VERIFICATION_FAILED"));
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

    /** 기프티콘 구매(본인 즉시 귀속) → gifticonId 반환. */
    long createGifticon(User owner, int amount) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/gifticons")
                        .header("Authorization", fixtures.authHeader(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"amount\":%d,\"receiverId\":%d}", amount, owner.getId())))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString())
                .at("/gifticonId").asLong();
    }

    /** 기프티콘 상세 조회로 현재 잔액이 기대값과 같은지 검증. */
    void assertGifticonBalance(User owner, long gifticonId, int expectedBalance) throws Exception {
        mockMvc.perform(get("/api/gifticons/" + gifticonId)
                        .header("Authorization", fixtures.authHeader(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(expectedBalance));
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

    /** 카카오페이 ready → approve → verify(PAID)까지 수행. */
    void payByKakao(User customer, String merchantUid, int amount) throws Exception {
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
