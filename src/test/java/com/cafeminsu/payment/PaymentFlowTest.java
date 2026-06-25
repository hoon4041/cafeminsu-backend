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

        // 1) prepare (기프티콘 없음 = 전액 카드)
        MvcResult prepareRes = mockMvc.perform(post("/api/payments/prepare")
                        .header("Authorization", fixtures.authHeader(s.customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"orderId\":%d}", s.orderId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchantUid").isString())
                .andExpect(jsonPath("$.gifticonAmount").value(0))
                .andExpect(jsonPath("$.cardAmount").value(10000))
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

        // 1) prepare — 기프티콘 선택만. 서버가 3000(차감) + 7000(카드)로 분할
        MvcResult prepareRes = mockMvc.perform(post("/api/payments/prepare")
                        .header("Authorization", fixtures.authHeader(s.customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"orderId\":%d,\"useGifticonId\":%d}", s.orderId, gifticonId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gifticonAmount").value(3000))
                .andExpect(jsonPath("$.cardAmount").value(7000))
                .andExpect(jsonPath("$.status").value("READY"))
                .andReturn();
        String merchantUid = objectMapper.readTree(prepareRes.getResponse().getContentAsString())
                .at("/merchantUid").asText();

        // 2) 카드 결제분 카카오페이 승인 + verify → 기프티콘도 함께 차감
        payByKakao(s.customer, merchantUid, 7000);

        // 3) 기프티콘 잔액이 3000 → 0으로 차감되었는지 확인
        assertGifticonBalance(s.customer, gifticonId, 0);
    }

    @Test
    @DisplayName("기프티콘 잔액 > 주문액 — prepare 한 번으로 즉시 확정(카드·카카오페이 불필요), 주문액만큼만 차감")
    void fullGifticonPaymentWithCap() throws Exception {
        Setup s = setup();   // 주문 총액 10000
        long gifticonId = createGifticon(s.customer, 15000);   // 잔액 15000 (> 주문액)

        // prepare 한 번으로 차감액 캡(10000) + 즉시 PAID 확정 (verify·카카오페이 없음)
        mockMvc.perform(post("/api/payments/prepare")
                        .header("Authorization", fixtures.authHeader(s.customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"orderId\":%d,\"useGifticonId\":%d}", s.orderId, gifticonId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gifticonAmount").value(10000))
                .andExpect(jsonPath("$.cardAmount").value(0))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.paymentId").isNumber());

        // 10000만 차감되어 잔액 5000 남음
        assertGifticonBalance(s.customer, gifticonId, 5000);
    }

    @Test
    @DisplayName("기프티콘 잔액 < 주문액 — 잔액만큼 차감하고 나머지는 카드")
    void gifticonBalanceLessThanOrder() throws Exception {
        Setup s = setup();   // 주문 총액 10000
        long gifticonId = createGifticon(s.customer, 2000);   // 잔액 2000 (< 주문액)

        // 서버가 기프티콘 2000 + 카드 8000으로 분할
        MvcResult prepareRes = mockMvc.perform(post("/api/payments/prepare")
                        .header("Authorization", fixtures.authHeader(s.customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"orderId\":%d,\"useGifticonId\":%d}", s.orderId, gifticonId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gifticonAmount").value(2000))
                .andExpect(jsonPath("$.cardAmount").value(8000))
                .andReturn();
        String merchantUid = objectMapper.readTree(prepareRes.getResponse().getContentAsString())
                .at("/merchantUid").asText();

        payByKakao(s.customer, merchantUid, 8000);
        assertGifticonBalance(s.customer, gifticonId, 0);
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
                                "{\"orderId\":%d,\"useGifticonId\":%d}", s.orderId, gifticonId)))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("같은 주문에 prepare 두 번 호출 시 거부")
    void duplicatePrepareRejected() throws Exception {
        Setup s = setup();
        prepare(s);

        // 두 번째 prepare
        mockMvc.perform(post("/api/payments/prepare")
                        .header("Authorization", fixtures.authHeader(s.customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"orderId\":%d}", s.orderId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_STATUS"));
    }

    @Test
    @DisplayName("카카오페이 ready를 거치지 않은 결제는 verify 실패")
    void verifyFailsWithoutKakaoPay() throws Exception {
        Setup s = setup();
        String merchantUid = prepare(s);

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
                        .content(String.format("{\"orderId\":%d}", s.orderId)))
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

    /** 전액 카드 결제 prepare → merchantUid 반환. */
    String prepare(Setup s) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/payments/prepare")
                        .header("Authorization", fixtures.authHeader(s.customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"orderId\":%d}", s.orderId)))
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
