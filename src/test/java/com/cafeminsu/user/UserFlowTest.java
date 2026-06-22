package com.cafeminsu.user;

import com.cafeminsu.domain.user.entity.User;
import com.cafeminsu.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserFlowTest extends IntegrationTestSupport {

    @Test
    @DisplayName("내 프로필 조회 — role 포함")
    void getMyProfile() throws Exception {
        User user = fixtures.createCustomer("민수");

        mockMvc.perform(get("/api/user/profile")
                        .header("Authorization", fixtures.authHeader(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    @DisplayName("닉네임 변경 — 성공")
    void changeNickname() throws Exception {
        User user = fixtures.createCustomer("기존");

        mockMvc.perform(patch("/api/user/nickname")
                        .header("Authorization", fixtures.authHeader(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"새이름\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("닉네임 변경 — 다른 사람과 중복 시 충돌(2201)")
    void changeNickname_duplicated() throws Exception {
        // fixtures가 nickname에 일련번호 suffix를 붙이므로, 정확한 충돌을 만들기 위해
        // 먼저 한 명의 nickname을 확인하고 그 값으로 다른 사람이 변경 시도
        User other = fixtures.createCustomer("선점");
        String takenNickname = other.getNickname();

        User user = fixtures.createCustomer("변경자");

        mockMvc.perform(patch("/api/user/nickname")
                        .header("Authorization", fixtures.authHeader(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"" + takenNickname + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NICKNAME_DUPLICATED"));
    }

    @Test
    @DisplayName("위치 저장 후 조회 시 같은 값")
    void saveAndGetLocation() throws Exception {
        User user = fixtures.createCustomer("위치맨");

        mockMvc.perform(post("/api/user/location")
                        .header("Authorization", fixtures.authHeader(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":37.4503,\"longitude\":126.7314}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/user/location")
                        .header("Authorization", fixtures.authHeader(user)))
                .andExpect(jsonPath("$.latitude").value(37.4503))
                .andExpect(jsonPath("$.longitude").value(126.7314));
    }

    @Test
    @DisplayName("점주 전환 후 role=OWNER")
    void becomeOwner() throws Exception {
        User user = fixtures.createCustomer("점주지망");

        mockMvc.perform(post("/api/user/become-owner")
                        .header("Authorization", fixtures.authHeader(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("OWNER"));
    }

    @Test
    @DisplayName("인증 없이 프로필 조회 시 401")
    void unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/user/profile"))
                .andExpect(status().isUnauthorized());
    }
}
