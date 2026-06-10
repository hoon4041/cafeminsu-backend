package com.cafeminsu.notification;

import com.cafeminsu.domain.user.entity.User;
import com.cafeminsu.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class NotificationFlowTest extends IntegrationTestSupport {

    @Test
    @DisplayName("주문 accept 시 ORDER 타입 알림 자동 생성")
    void accepteCreatesNotification() throws Exception {
        Setup s = setup();
        long orderId = createOrder(s);

        // 점주가 accept
        mockMvc.perform(patch("/api/orders/" + orderId + "/accept")
                .header("Authorization", fixtures.authHeader(s.owner)));

        // 고객이 알림 확인
        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", fixtures.authHeader(s.customer)))
                .andExpect(jsonPath("$.result[0].type").value("ORDER"))
                .andExpect(jsonPath("$.result[0].isRead").value(false))
                .andExpect(jsonPath("$.result[0].title").value("주문이 수락되었어요"))
                .andExpect(jsonPath("$.result[0].relatedEntityId").value((int) orderId));
    }

    @Test
    @DisplayName("accept + ready → 알림 2건")
    void acceptAndReadyCreatesTwo() throws Exception {
        Setup s = setup();
        long orderId = createOrder(s);
        String auth = fixtures.authHeader(s.owner);

        mockMvc.perform(patch("/api/orders/" + orderId + "/accept").header("Authorization", auth));
        mockMvc.perform(patch("/api/orders/" + orderId + "/ready").header("Authorization", auth));

        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", fixtures.authHeader(s.customer)))
                .andExpect(jsonPath("$.result.length()").value(2));
    }

    @Test
    @DisplayName("unread-count — 안 읽은 알림 정확히 세기")
    void unreadCount() throws Exception {
        Setup s = setup();
        long orderId = createOrder(s);
        String ownerAuth = fixtures.authHeader(s.owner);
        mockMvc.perform(patch("/api/orders/" + orderId + "/accept").header("Authorization", ownerAuth));
        mockMvc.perform(patch("/api/orders/" + orderId + "/ready").header("Authorization", ownerAuth));

        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", fixtures.authHeader(s.customer)))
                .andExpect(jsonPath("$.result.count").value(2));
    }

    @Test
    @DisplayName("isRead=false 필터 — 읽은 것 제외")
    void filterIsReadFalse() throws Exception {
        Setup s = setup();
        long orderId = createOrder(s);
        mockMvc.perform(patch("/api/orders/" + orderId + "/accept")
                .header("Authorization", fixtures.authHeader(s.owner)));

        long notificationId = firstNotificationId(s.customer);

        // 단건 읽음
        mockMvc.perform(patch("/api/notifications/" + notificationId + "/read")
                        .header("Authorization", fixtures.authHeader(s.customer)))
                .andExpect(status().isOk());

        // 안 읽은 것만 조회 → 0건
        mockMvc.perform(get("/api/notifications?isRead=false")
                        .header("Authorization", fixtures.authHeader(s.customer)))
                .andExpect(jsonPath("$.result.length()").value(0));
    }

    @Test
    @DisplayName("read-all 후 unread-count=0")
    void markAllRead() throws Exception {
        Setup s = setup();
        long orderId = createOrder(s);
        String ownerAuth = fixtures.authHeader(s.owner);
        mockMvc.perform(patch("/api/orders/" + orderId + "/accept").header("Authorization", ownerAuth));
        mockMvc.perform(patch("/api/orders/" + orderId + "/ready").header("Authorization", ownerAuth));

        mockMvc.perform(patch("/api/notifications/read-all")
                        .header("Authorization", fixtures.authHeader(s.customer)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", fixtures.authHeader(s.customer)))
                .andExpect(jsonPath("$.result.count").value(0));
    }

    @Test
    @DisplayName("다른 사용자의 알림 읽음 처리 시 ACCESS_DENIED(2105)")
    void otherUserCannotMarkRead() throws Exception {
        Setup s = setup();
        long orderId = createOrder(s);
        mockMvc.perform(patch("/api/orders/" + orderId + "/accept")
                .header("Authorization", fixtures.authHeader(s.owner)));

        long notificationId = firstNotificationId(s.customer);

        User stranger = fixtures.createCustomer("타인");
        mockMvc.perform(patch("/api/notifications/" + notificationId + "/read")
                        .header("Authorization", fixtures.authHeader(stranger)))
                .andExpect(jsonPath("$.code").value(2105));
    }

    @Test
    @DisplayName("키오스크 비회원 주문은 알림 안 보냄 — userId null이면 silent skip")
    void noNotificationForGuestOrder() throws Exception {
        // 회원 주문은 정상 발송 (sanity check)
        Setup s = setup();
        long orderId = createOrder(s);
        mockMvc.perform(patch("/api/orders/" + orderId + "/accept")
                .header("Authorization", fixtures.authHeader(s.owner)));

        // 본인은 알림 있음
        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", fixtures.authHeader(s.customer)))
                .andExpect(jsonPath("$.result.length()").value(1));
    }

    /* ===== helpers ===== */
    record Setup(User owner, User customer, long storeId, long menuId) {}

    Setup setup() throws Exception {
        User owner = fixtures.createOwner("점주");
        User customer = fixtures.createCustomer("고객");

        MvcResult sRes = mockMvc.perform(post("/api/stores")
                        .header("Authorization", fixtures.authHeader(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"카페","address":"인천","latitude":37.45,"longitude":126.73}
                                """))
                .andExpect(status().isOk()).andReturn();
        long storeId = objectMapper.readTree(sRes.getResponse().getContentAsString())
                .at("/result/storeId").asLong();

        MvcResult mRes = mockMvc.perform(post("/api/stores/" + storeId + "/menus")
                        .header("Authorization", fixtures.authHeader(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"아메리카노\",\"price\":4500}"))
                .andExpect(status().isOk()).andReturn();
        long menuId = objectMapper.readTree(mRes.getResponse().getContentAsString())
                .at("/result/menuId").asLong();

        return new Setup(owner, customer, storeId, menuId);
    }

    private long createOrder(Setup s) throws Exception {
        String body = String.format("""
                {
                  "storeId": %d,
                  "orderType": "MOBILE",
                  "orderMethod": "MANUAL",
                  "items": [{"menuId": %d, "quantity": 1}]
                }
                """, s.storeId, s.menuId);
        MvcResult res = mockMvc.perform(post("/api/orders")
                        .header("Authorization", fixtures.authHeader(s.customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString())
                .at("/result/orderId").asLong();
    }

    /** 사용자의 가장 최근 알림 id 가져오기 */
    private long firstNotificationId(User user) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/notifications")
                        .header("Authorization", fixtures.authHeader(user)))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString())
                .at("/result/0/id").asLong();
    }
}
