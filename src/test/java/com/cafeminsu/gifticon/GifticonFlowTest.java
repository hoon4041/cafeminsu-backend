package com.cafeminsu.gifticon;

import com.cafeminsu.domain.gifticon.dto.GifticonUseReq;
import com.cafeminsu.domain.gifticon.dto.GifticonUseRes;
import com.cafeminsu.domain.gifticon.entity.GifticonStatus;
import com.cafeminsu.domain.gifticon.service.GifticonService;
import com.cafeminsu.domain.user.entity.User;
import com.cafeminsu.global.common.BaseResponseStatus;
import com.cafeminsu.global.exception.BaseException;
import com.cafeminsu.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GifticonFlowTest extends IntegrationTestSupport {

    @Autowired
    private GifticonService gifticonService;

    @Test
    @DisplayName("기프티콘 구매 — 수신자 미지정, claimCode/shareLink 발급")
    void purchaseWithoutReceiver() throws Exception {
        User sender = fixtures.createCustomer("보내는사람");

        mockMvc.perform(post("/api/gifticons")
                        .header("Authorization", fixtures.authHeader(sender))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":50000,\"message\":\"오늘 하루 수고했어\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gifticonId").isNumber())
                .andExpect(jsonPath("$.claimCode").isString())
                .andExpect(jsonPath("$.shareLink").isString())
                .andExpect(jsonPath("$.amount").value(50000));
    }

    @Test
    @DisplayName("기프티콘 구매 — receiverId 즉시 지정(하위호환)")
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
                .andExpect(jsonPath("$.claimCode").isString());
    }

    @Test
    @DisplayName("등록(claim) — 코드로 받는 사람 계정에 귀속 후 /my 노출")
    void claimByReceiver() throws Exception {
        User sender = fixtures.createCustomer("보낸사람");
        User receiver = fixtures.createCustomer("받은사람");

        // 수신자 미지정 발행
        MvcResult res = mockMvc.perform(post("/api/gifticons")
                        .header("Authorization", fixtures.authHeader(sender))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":30000}"))
                .andExpect(status().isOk()).andReturn();
        String claimCode = objectMapper.readTree(res.getResponse().getContentAsString())
                .at("/claimCode").asText();

        // 받는 사람이 등록
        mockMvc.perform(post("/api/gifticons/claim")
                        .header("Authorization", fixtures.authHeader(receiver))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"claimCode\":\"" + claimCode + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(30000))
                .andExpect(jsonPath("$.status").value("UNUSED"));

        // 이후 사용 가능 목록(/my)에 노출
        mockMvc.perform(get("/api/gifticons/my")
                        .header("Authorization", fixtures.authHeader(receiver)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].balance").value(30000));
    }

    @Test
    @DisplayName("등록(claim) — 같은 사람 재등록은 멱등 성공")
    void claimIdempotentForSameUser() throws Exception {
        User sender = fixtures.createCustomer("보낸사람");
        User receiver = fixtures.createCustomer("받은사람");
        String claimCode = purchaseUnassigned(sender, 30000);

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/gifticons/claim")
                            .header("Authorization", fixtures.authHeader(receiver))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"claimCode\":\"" + claimCode + "\"}"))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("등록(claim) — 이미 타인이 등록하면 ALREADY_CLAIMED(409)")
    void claimAlreadyClaimedByOther() throws Exception {
        User sender = fixtures.createCustomer("보낸사람");
        User first = fixtures.createCustomer("먼저등록");
        User second = fixtures.createCustomer("나중에등록");
        String claimCode = purchaseUnassigned(sender, 30000);

        mockMvc.perform(post("/api/gifticons/claim")
                        .header("Authorization", fixtures.authHeader(first))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"claimCode\":\"" + claimCode + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/gifticons/claim")
                        .header("Authorization", fixtures.authHeader(second))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"claimCode\":\"" + claimCode + "\"}"))
                .andExpect(jsonPath("$.code").value("GIFTICON_ALREADY_CLAIMED"));
    }

    @Test
    @DisplayName("등록(claim) — 존재하지 않는 코드는 INVALID_CODE")
    void claimInvalidCode() throws Exception {
        User receiver = fixtures.createCustomer("받은사람");

        mockMvc.perform(post("/api/gifticons/claim")
                        .header("Authorization", fixtures.authHeader(receiver))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"claimCode\":\"GFT-XXXX-XXXX\"}"))
                .andExpect(jsonPath("$.code").value("GIFTICON_INVALID_CODE"));
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
    @DisplayName("부분 사용 후 잔액·상태 갱신")
    void partialUse() throws Exception {
        User sender = fixtures.createCustomer("보낸사람");
        User receiver = fixtures.createCustomer("받은사람");
        IssueResult issued = issueGifticon(sender, receiver, 50000);

        long orderId = createDummyOrder(sender, receiver);

        // 차감은 결제(PaymentService)에서 내부 호출된다. 도메인 차감 로직을 서비스로 직접 검증.
        GifticonUseRes res = gifticonService.use(issued.gifticonId, new GifticonUseReq(orderId, 4500));
        assertEquals(45500, res.balanceAfter());
        assertEquals(GifticonStatus.PARTIAL, res.status());
    }

    @Test
    @DisplayName("잔액보다 큰 금액 사용 시 INSUFFICIENT_BALANCE (2703)")
    void insufficientBalance() throws Exception {
        User sender = fixtures.createCustomer("보낸사람");
        User receiver = fixtures.createCustomer("받은사람");
        IssueResult issued = issueGifticon(sender, receiver, 1000);

        long orderId = createDummyOrder(sender, receiver);

        BaseException ex = assertThrows(BaseException.class,
                () -> gifticonService.use(issued.gifticonId, new GifticonUseReq(orderId, 99999)));
        assertEquals(BaseResponseStatus.GIFTICON_INSUFFICIENT_BALANCE, ex.getStatus());
    }

    @Test
    @DisplayName("잔액이 0이 되면 status=USED, 추가 사용 거부")
    void fullyUsedRejectsFurtherUse() throws Exception {
        User sender = fixtures.createCustomer("보낸사람");
        User receiver = fixtures.createCustomer("받은사람");
        IssueResult issued = issueGifticon(sender, receiver, 5000);

        long orderId = createDummyOrder(sender, receiver);

        // 전액 사용
        GifticonUseRes res = gifticonService.use(issued.gifticonId, new GifticonUseReq(orderId, 5000));
        assertEquals(GifticonStatus.USED, res.status());
        assertEquals(0, res.balanceAfter());

        // 추가 사용 시도
        BaseException ex = assertThrows(BaseException.class,
                () -> gifticonService.use(issued.gifticonId, new GifticonUseReq(orderId, 100)));
        assertEquals(BaseResponseStatus.GIFTICON_ALREADY_USED, ex.getStatus());
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
    record IssueResult(long gifticonId, String claimCode) {}

    /** 수신자 미지정으로 구매하고 claimCode 반환. */
    String purchaseUnassigned(User sender, int amount) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/gifticons")
                        .header("Authorization", fixtures.authHeader(sender))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"amount\":%d}", amount)))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString())
                .at("/claimCode").asText();
    }

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
                root.at("/claimCode").asText()
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
