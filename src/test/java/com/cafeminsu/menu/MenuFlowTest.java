package com.cafeminsu.menu;

import com.cafeminsu.domain.user.entity.User;
import com.cafeminsu.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MenuFlowTest extends IntegrationTestSupport {

    private static final String STORE_BODY = """
            {
              "name": "민수카페",
              "address": "인천 남동구",
              "latitude": 37.4503,
              "longitude": 126.7314
            }
            """;

    @Test
    @DisplayName("점주는 본인 매장에 메뉴를 등록할 수 있다")
    void createMenu() throws Exception {
        User owner = fixtures.createOwner("점주");
        long storeId = createStore(owner);

        mockMvc.perform(post("/api/stores/" + storeId + "/menus")
                        .header("Authorization", fixtures.authHeader(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"아메리카노\",\"price\":4500,\"category\":\"커피\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.menuId").isNumber());
    }

    @Test
    @DisplayName("다른 점주는 남의 매장에 메뉴를 등록할 수 없다 (소유권 체인)")
    void otherOwnerCannotCreateMenu() throws Exception {
        User owner1 = fixtures.createOwner("점주1");
        User owner2 = fixtures.createOwner("점주2");
        long storeId = createStore(owner1);

        mockMvc.perform(post("/api/stores/" + storeId + "/menus")
                        .header("Authorization", fixtures.authHeader(owner2))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"라떼\",\"price\":5000}"))
                .andExpect(jsonPath("$.code").value("NOT_STORE_OWNER"));
    }

    @Test
    @DisplayName("메뉴 상세 — 옵션 포함, 비로그인 OK")
    void menuDetailWithOptions() throws Exception {
        User owner = fixtures.createOwner("점주");
        long storeId = createStore(owner);
        long menuId = createMenu(owner, storeId, "아메리카노", 4500);
        addOption(owner, menuId, "size", "L", 500);

        mockMvc.perform(get("/api/menus/" + menuId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("아메리카노"))
                .andExpect(jsonPath("$.options[0].group").value("size"))
                .andExpect(jsonPath("$.options[0].additionalPrice").value(500));
    }

    @Test
    @DisplayName("메뉴 등록 시 옵션을 함께 보내면 한 번에 생성된다")
    void createMenuWithOptions() throws Exception {
        User owner = fixtures.createOwner("점주");
        long storeId = createStore(owner);

        MvcResult res = mockMvc.perform(post("/api/stores/" + storeId + "/menus")
                        .header("Authorization", fixtures.authHeader(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "아메리카노",
                                  "price": 4500,
                                  "options": [
                                    {"optionGroup":"size","optionName":"L","additionalPrice":500},
                                    {"optionGroup":"temp","optionName":"ICE","additionalPrice":0,"isDefault":true}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.menuId").isNumber())
                .andReturn();
        long menuId = objectMapper.readTree(res.getResponse().getContentAsString())
                .at("/menuId").asLong();

        // 상세 조회 시 옵션 2개가 함께 생성돼 있어야 함
        mockMvc.perform(get("/api/menus/" + menuId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options.length()").value(2));
    }

    @Test
    @DisplayName("옵션 없이(options 생략) 등록하면 메뉴만 생성된다 — 하위 호환")
    void createMenuWithoutOptions() throws Exception {
        User owner = fixtures.createOwner("점주");
        long storeId = createStore(owner);
        long menuId = createMenu(owner, storeId, "라떼", 5000);

        mockMvc.perform(get("/api/menus/" + menuId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options.length()").value(0));
    }

    @Test
    @DisplayName("함께 보낸 옵션이 검증 실패하면 메뉴도 생성되지 않는다 (원자성)")
    void invalidOptionRollsBackMenu() throws Exception {
        User owner = fixtures.createOwner("점주");
        long storeId = createStore(owner);

        // optionName 누락 → 400, 메뉴 자체도 만들어지면 안 됨
        mockMvc.perform(post("/api/stores/" + storeId + "/menus")
                        .header("Authorization", fixtures.authHeader(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "콜드브루",
                                  "price": 5500,
                                  "options": [ {"optionGroup":"size","additionalPrice":500} ]
                                }
                                """))
                .andExpect(status().isBadRequest());

        // 메뉴 목록이 비어 있어야 함 (롤백 확인)
        mockMvc.perform(get("/api/stores/" + storeId + "/menus"))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("판매 토글 — false로 바꾸면 isAvailable=false")
    void toggleAvailability() throws Exception {
        User owner = fixtures.createOwner("점주");
        long storeId = createStore(owner);
        long menuId = createMenu(owner, storeId, "아메리카노", 4500);

        mockMvc.perform(patch("/api/menus/" + menuId + "/availability")
                        .header("Authorization", fixtures.authHeader(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isAvailable\":false}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/menus/" + menuId))
                .andExpect(jsonPath("$.isAvailable").value(false));
    }

    @Test
    @DisplayName("메뉴 soft delete 후 상세 조회 시 NOT_FOUND")
    void softDeleteMenu() throws Exception {
        User owner = fixtures.createOwner("점주");
        long storeId = createStore(owner);
        long menuId = createMenu(owner, storeId, "아메리카노", 4500);

        mockMvc.perform(delete("/api/menus/" + menuId)
                        .header("Authorization", fixtures.authHeader(owner)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/menus/" + menuId))
                .andExpect(jsonPath("$.code").value("MENU_NOT_FOUND"));
    }

    /* ===== helpers ===== */
    private long createStore(User owner) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/stores")
                        .header("Authorization", fixtures.authHeader(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(STORE_BODY))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString())
                .at("/storeId").asLong();
    }

    private long createMenu(User owner, long storeId, String name, int price) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/stores/" + storeId + "/menus")
                        .header("Authorization", fixtures.authHeader(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"price\":" + price + "}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString())
                .at("/menuId").asLong();
    }

    private void addOption(User owner, long menuId, String group, String name, int addPrice) throws Exception {
        mockMvc.perform(post("/api/menus/" + menuId + "/options")
                        .header("Authorization", fixtures.authHeader(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"optionGroup\":\"" + group + "\",\"optionName\":\"" + name
                                + "\",\"additionalPrice\":" + addPrice + "}"))
                .andExpect(status().isOk());
    }
}
