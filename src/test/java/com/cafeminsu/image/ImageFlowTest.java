package com.cafeminsu.image;

import com.cafeminsu.domain.user.entity.User;
import com.cafeminsu.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ImageFlowTest extends IntegrationTestSupport {

    private MockMultipartFile pngFile() {
        return new MockMultipartFile("file", "menu.png", "image/png", new byte[]{1, 2, 3});
    }

    @Test
    @DisplayName("점주는 메뉴 이미지를 업로드하고 업로드 URL을 받는다")
    void ownerCanUpload() throws Exception {
        User owner = fixtures.createOwner("점주");

        mockMvc.perform(multipart("/api/images/menu")
                        .file(pngFile())
                        .header("Authorization", fixtures.authHeader(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.imageUrl").value(startsWith("/imgs/menu/uploads/")));
    }

    @Test
    @DisplayName("일반 고객은 메뉴 이미지를 업로드할 수 없다 (403)")
    void customerCannotUpload() throws Exception {
        // 권한 거부는 Security 필터 레벨(authorizeHttpRequests의 hasRole)에서 발생하므로
        // 기본 AccessDeniedHandler가 빈 본문 403을 반환한다(서비스 레벨 2105 본문 아님).
        // StoreFlowTest.customerCannotCreateStore 와 동일한 패턴.
        User customer = fixtures.createCustomer("고객");

        mockMvc.perform(multipart("/api/images/menu")
                        .file(pngFile())
                        .header("Authorization", fixtures.authHeader(customer)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("비로그인은 업로드할 수 없다")
    void unauthenticatedCannotUpload() throws Exception {
        mockMvc.perform(multipart("/api/images/menu").file(pngFile()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("허용되지 않은 확장자는 거부된다")
    void badTypeRejected() throws Exception {
        User owner = fixtures.createOwner("점주");
        MockMultipartFile gif = new MockMultipartFile("file", "x.gif", "image/gif", new byte[]{1});

        mockMvc.perform(multipart("/api/images/menu")
                        .file(gif)
                        .header("Authorization", fixtures.authHeader(owner)))
                .andExpect(jsonPath("$.code").value(2006));   // UNSUPPORTED_IMAGE_TYPE
    }
}
