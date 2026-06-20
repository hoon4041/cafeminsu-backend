package com.cafeminsu.global.storage;

import com.cafeminsu.global.common.BaseResponseStatus;
import com.cafeminsu.global.exception.BaseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileStorageServiceTest {

    @TempDir Path tempDir;
    FileStorageService service;

    @BeforeEach
    void setUp() {
        service = new FileStorageService(tempDir.toString(), 5_242_880L);
    }

    @Test
    @DisplayName("정상 이미지 저장 시 업로드 URL 접두를 반환하고 파일이 생성된다")
    void storeOk() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "menu.png", "image/png", new byte[]{1, 2, 3});

        String url = service.store(file);

        assertThat(url).startsWith("/imgs/menu/uploads/").endsWith(".png");
        String name = url.substring("/imgs/menu/uploads/".length());
        assertThat(Files.exists(tempDir.resolve("menu").resolve(name))).isTrue();
    }

    @Test
    @DisplayName("빈 파일은 예외")
    void emptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "x.png", "image/png", new byte[0]);
        assertThatThrownBy(() -> service.store(file))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getStatus())
                .isEqualTo(BaseResponseStatus.EMPTY_FILE);
    }

    @Test
    @DisplayName("허용되지 않은 확장자는 예외")
    void badExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "x.gif", "image/gif", new byte[]{1});
        assertThatThrownBy(() -> service.store(file))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getStatus())
                .isEqualTo(BaseResponseStatus.UNSUPPORTED_IMAGE_TYPE);
    }

    @Test
    @DisplayName("최대 용량 초과는 예외")
    void tooBig() {
        FileStorageService small = new FileStorageService(tempDir.toString(), 2L);
        MockMultipartFile file = new MockMultipartFile("file", "x.png", "image/png", new byte[]{1, 2, 3});
        assertThatThrownBy(() -> small.store(file))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getStatus())
                .isEqualTo(BaseResponseStatus.IMAGE_SIZE_EXCEEDED);
    }

    @Test
    @DisplayName("업로드 파일만 삭제되고, 접두가 다른 URL은 건드리지 않는다")
    void deleteOnlyUploads() {
        MockMultipartFile file = new MockMultipartFile("file", "x.png", "image/png", new byte[]{1});
        String url = service.store(file);
        String name = url.substring("/imgs/menu/uploads/".length());
        Path stored = tempDir.resolve("menu").resolve(name);
        assertThat(Files.exists(stored)).isTrue();

        // 번들 에셋류 URL은 no-op
        service.delete("/imgs/menu/americano.svg");
        assertThat(Files.exists(stored)).isTrue();

        // 업로드 URL은 실제 삭제
        service.delete(url);
        assertThat(Files.exists(stored)).isFalse();
    }
}
