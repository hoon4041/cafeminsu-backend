package com.cafeminsu.menu;

import com.cafeminsu.domain.user.entity.User;
import com.cafeminsu.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MenuImageLifecycleTest extends IntegrationTestSupport {

    private static final String UPLOAD_PREFIX = "/imgs/menu/uploads/";

    @Value("${file.upload-dir}")
    private String uploadDir;

    private static final String STORE_BODY = """
            {
              "name": "민수카페",
              "address": "인천 남동구",
              "latitude": 37.4503,
              "longitude": 126.7314
            }
            """;

    @Test
    @DisplayName("메뉴 삭제 시 업로드 이미지 파일도 삭제된다")
    void deleteMenuRemovesUploadedImage() throws Exception {
        User owner = fixtures.createOwner("점주");
        long storeId = createStore(owner);
        String imageUrl = uploadImage(owner);
        Path file = resolve(imageUrl);
        assertThat(Files.exists(file)).isTrue();

        long menuId = createMenuWithImage(owner, storeId, "아메리카노", 4500, imageUrl);

        mockMvc.perform(delete("/api/menus/" + menuId)
                        .header("Authorization", fixtures.authHeader(owner)))
                .andExpect(status().isOk());

        assertThat(Files.exists(file)).isFalse();
    }

    /* ===== helpers ===== */
    private Path resolve(String imageUrl) {
        String name = imageUrl.substring(UPLOAD_PREFIX.length());
        return Paths.get(uploadDir, "menu", name).toAbsolutePath().normalize();
    }

    private String uploadImage(User owner) throws Exception {
        MvcResult res = mockMvc.perform(multipart("/api/images/menu")
                        .file(new MockMultipartFile("file", "menu.png", "image/png", new byte[]{1, 2, 3}))
                        .header("Authorization", fixtures.authHeader(owner)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString())
                .at("/result/imageUrl").asText();
    }

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

    private long createMenuWithImage(User owner, long storeId, String name, int price, String imageUrl) throws Exception {
        String body = "{\"name\":\"" + name + "\",\"price\":" + price
                + ",\"imageUrl\":\"" + imageUrl + "\"}";
        MvcResult res = mockMvc.perform(post("/api/stores/" + storeId + "/menus")
                        .header("Authorization", fixtures.authHeader(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString())
                .at("/result/menuId").asLong();
    }
}
