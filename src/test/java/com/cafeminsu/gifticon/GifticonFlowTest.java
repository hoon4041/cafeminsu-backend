package com.cafeminsu.gifticon;

import com.cafeminsu.domain.user.entity.User;
import com.cafeminsu.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GifticonFlowTest extends IntegrationTestSupport {

    @Test
    @DisplayName("기프티콘 발행 — receiverId 지정")
    void issueWithReceiverId() throws Exception {
        User sender = fixtures.createCustomer("보내는사람");
        User receiver = fixtures.createCustomer("받는사람");

        mockMvc.perform(post("/api/gifticons")
                        .header("Authorization", fixtures.authHeader(sender))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"amount\":50000,\"receiverId\":%d,\"message\":\"생일축하\"}",
                                receiver.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gifticonId").isNumber())
                .andExpect(jsonPath("$.qrCode").isString());
    }

    @Test
    @DisplayName("받은 기프티콘 목록 — 잔액과 상태 확인")
    void receivedList() throws Exception {
        User sender = fixtures.createCustomer("보낸사람");
        User receiver = fixtures.createCustomer("받은사람");
        issueGifticon(sender, receiver, 50000);

        mockMvc.perform(get("/api/gifticons/received")
                        .header("Authorization", fixtures.authHeader(receiver)))
                .andExpect(jsonPath("$[0].amount").value(50000))
                .andExpect(jsonPath("$[0].balance").value(50000))
                .andExpect(jsonPath("$[0].status").value("UNUSED"));
    }

    @Test
    @DisplayName("QR 검증 — 유효한 QR")
    void validateValidQr() throws Exception {
        User sender = fixtures.createCustomer("보낸사람");
        User receiver = fixtures.createCustomer("받은사람");
        IssueResult issued = issueGifticon(sender, receiver, 50000);

        mockMvc.perform(post("/api/gifticons/redeem/validate")
                        .header("Authorization", fixtures.authHeader(sender))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"qrCode\":\"" + issued.qrCode + "\"}"))
                .andExpect(jsonPath("$.isValid").value(true))
                .andExpect(jsonPath("$.balance").value(50000));
    }

    @Test
    @DisplayName("부분 사용 후 잔액·상태 갱신")
    void partialUse() throws Exception {
        User sender = fixtures.createCustomer("보낸사람");
        User receiver = fixtures.createCustomer("받은사람");
        IssueResult issued = issueGifticon(sender, receiver, 50000);

        long orderId = createDummyOrder(sender, receiver);

        mockMvc.perform(post("/api/gifticons/" + issued.gifticonId + "/use")
                        .header("Authorization", fixtures.authHeader(receiver))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"orderId\":%d,\"usedAmount\":4500}", orderId)))
                .andExpect(jsonPath("$.balanceAfter").value(45500))
                .andExpect(jsonPath("$.status").value("PARTIAL"));
    }

    @Test
    @DisplayName("잔액보다 큰 금액 사용 시 INSUFFICIENT_BALANCE (2703)")
    void insufficientBalance() throws Exception {
        User sender = fixtures.createCustomer("보낸사람");
        User receiver = fixtures.createCustomer("받은사람");
        IssueResult issued = issueGifticon(sender, receiver, 1000);

        long orderId = createDummyOrder(sender, receiver);

        mockMvc.perform(post("/api/gifticons/" + issued.gifticonId + "/use")
                        .header("Authorization", fixtures.authHeader(receiver))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"orderId\":%d,\"usedAmount\":99999}", orderId)))
                .andExpect(jsonPath("$.code").value("GIFTICON_INSUFFICIENT_BALANCE"));
    }

    @Test
    @DisplayName("잔액이 0이 되면 status=USED, 추가 사용 거부")
    void fullyUsedRejectsFurtherUse() throws Exception {
        User sender = fixtures.createCustomer("보낸사람");
        User receiver = fixtures.createCustomer("받은사람");
        IssueResult issued = issueGifticon(sender, receiver, 5000);

        long orderId = createDummyOrder(sender, receiver);

        // 전액 사용
        mockMvc.perform(post("/api/gifticons/" + issued.gifticonId + "/use")
                        .header("Authorization", fixtures.authHeader(receiver))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"orderId\":%d,\"usedAmount\":5000}", orderId)))
                .andExpect(jsonPath("$.status").value("USED"))
                .andExpect(jsonPath("$.balanceAfter").value(0));

        // 추가 사용 시도
        mockMvc.perform(post("/api/gifticons/" + issued.gifticonId + "/use")
                        .header("Authorization", fixtures.authHeader(receiver))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"orderId\":%d,\"usedAmount\":100}", orderId)))
                .andExpect(jsonPath("$.code").value("GIFTICON_ALREADY_USED"));
    }

    @Test
    @DisplayName("타인은 기프티콘 상세 조회 불가")
    void outsiderCannotViewDetail() throws Exception {
        User sender = fixtures.createCustomer("보낸사람");
        User receiver = fixtures.createCustomer("받은사람");
        User stranger = fixtures.createCustomer("타인");
        IssueResult issued = issueGifticon(sender, receiver, 50000);

        mockMvc.perform(get("/api/gifticons/" + issued.gifticonId)
                        .header("Authorization", fixtures.authHeader(stranger)))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    /* ===== helpers ===== */
    record IssueResult(long gifticonId, String qrCode) {}

    IssueResult issueGifticon(User sender, User receiver, int amount) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/gifticons")
                        .header("Authorization", fixtures.authHeader(sender))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"amount\":%d,\"receiverId\":%d}",
                                amount, receiver.getId())))
                .andExpect(status().isOk()).andReturn();
        var root = objectMapper.readTree(res.getResponse().getContentAsString());
        return new IssueResult(
                root.at("/gifticonId").asLong(),
                root.at("/qrCode").asText()
        );
    }

    /**
     * 기프티콘 use 호출에 필요한 더미 주문 1개 만들기.
     * 매장·메뉴 만들고 receiver(고객) 명의로 5000원짜리 주문 생성.
     */
    long createDummyOrder(User someOwnerCustomer, User customer) throws Exception {
        User owner = fixtures.createOwner("매장점주");

        MvcResult sRes = mockMvc.perform(post("/api/stores")
                        .header("Authorization", fixtures.authHeader(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"카페","address":"인천","latitude":37.45,"longitude":126.73}
                                """))
                .andExpect(status().isOk()).andReturn();
        long storeId = objectMapper.readTree(sRes.getResponse().getContentAsString())
                .at("/storeId").asLong();

        MvcResult mRes = mockMvc.perform(post("/api/stores/" + storeId + "/menus")
                        .header("Authorization", fixtures.authHeader(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"커피\",\"price\":5000}"))
                .andExpect(status().isOk()).andReturn();
        long menuId = objectMapper.readTree(mRes.getResponse().getContentAsString())
                .at("/menuId").asLong();

        String orderBody = String.format("""
                {
                  "storeId": %d,
                  "orderType": "MOBILE",
                  "orderMethod": "MANUAL",
                  "items": [{"menuId": %d, "quantity": 1}]
                }
                """, storeId, menuId);
        MvcResult oRes = mockMvc.perform(post("/api/orders")
                        .header("Authorization", fixtures.authHeader(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(oRes.getResponse().getContentAsString())
                .at("/orderId").asLong();
    }
}
