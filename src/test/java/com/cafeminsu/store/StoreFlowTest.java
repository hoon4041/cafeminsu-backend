package com.cafeminsu.store;

import com.cafeminsu.domain.user.entity.User;
import com.cafeminsu.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StoreFlowTest extends IntegrationTestSupport {

    private static final String STORE_BODY = """
            {
              "name": "민수카페 인천점",
              "address": "인천광역시 남동구 구월동",
              "latitude": 37.4503,
              "longitude": 126.7314,
              "phone": "032-123-4567",
              "businessHours": "08:00-22:00"
            }
            """;

    @Test
    @DisplayName("점주는 매장을 등록할 수 있다")
    void ownerCanCreateStore() throws Exception {
        User owner = fixtures.createOwner("점주");

        mockMvc.perform(post("/api/stores")
                        .header("Authorization", fixtures.authHeader(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(STORE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.storeId").isNumber());
    }

    @Test
    @DisplayName("일반 고객은 매장을 등록할 수 없다 (403)")
    void customerCannotCreateStore() throws Exception {
        User customer = fixtures.createCustomer("고객");

        mockMvc.perform(post("/api/stores")
                        .header("Authorization", fixtures.authHeader(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(STORE_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("점주는 본인 매장을 수정할 수 있다")
    void ownerCanUpdateOwnStore() throws Exception {
        User owner = fixtures.createOwner("점주");
        long storeId = createStore(owner);

        mockMvc.perform(patch("/api/stores/" + storeId)
                        .header("Authorization", fixtures.authHeader(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"새이름\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true));
    }

    @Test
    @DisplayName("다른 점주는 남의 매장을 수정할 수 없다")
    void otherOwnerCannotUpdate() throws Exception {
        User owner1 = fixtures.createOwner("점주1");
        User owner2 = fixtures.createOwner("점주2");
        long storeId = createStore(owner1);

        mockMvc.perform(patch("/api/stores/" + storeId)
                        .header("Authorization", fixtures.authHeader(owner2))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"빼앗기\"}"))
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value(2301));   // NOT_STORE_OWNER
    }

    @Test
    @DisplayName("매장 삭제는 soft delete — 이후 상세 조회 시 NOT_FOUND")
    void softDelete() throws Exception {
        User owner = fixtures.createOwner("점주");
        long storeId = createStore(owner);

        mockMvc.perform(delete("/api/stores/" + storeId)
                        .header("Authorization", fixtures.authHeader(owner)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/stores/" + storeId))
                .andExpect(jsonPath("$.code").value(2300));   // STORE_NOT_FOUND
    }

    /* helper */
    private long createStore(User owner) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/stores")
                        .header("Authorization", fixtures.authHeader(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(STORE_BODY))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString())
                .at("/result/storeId").asLong();
    }
}
