# 메뉴 이미지 업로드 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 점주가 메뉴 이미지를 직접 업로드하고, 메뉴 수정 시 이미지 교체·이전 파일 정리, 삭제 시 업로드 파일 정리가 되는 백엔드를 추가한다.

**Architecture:** 업로드 전용 엔드포인트(`POST /api/images/menu`)가 파일을 외부 디렉토리에 저장하고 `/imgs/menu/uploads/{uuid}.{ext}` 형태의 URL을 반환한다. 기존 메뉴 등록/수정 JSON 계약은 그대로 두고 클라이언트가 받은 URL을 `imageUrl`로 넘긴다. 파일 라이프사이클은 `FileStorageService`로 캡슐화하며, 업로드 URL 접두(`/imgs/menu/uploads/`)로 번들 기본 svg와 구분해 그것만 삭제한다.

**Tech Stack:** Java 17, Spring Boot 3.3.5 (web/data-jpa/security/validation), JUnit5 + MockMvc + H2(test), Lombok.

## Global Constraints

- Java 17 toolchain, Spring Boot 3.3.5. (build.gradle 기준, 변경 금지)
- 모든 응답은 `BaseResponse<T>` 래퍼 사용. 성공 `BaseResponse.success(result)`, 실패는 `BaseException(BaseResponseStatus.XXX)` 던지면 `GlobalExceptionHandler`가 변환.
- 에러는 `BaseResponseStatus` enum에 코드로 추가(공통/시스템 영역 2000~2099). 안드로이드 팀이 `code`로 분기.
- 권한: 메뉴/이미지 쓰기 작업은 Security에서 `hasRole("OWNER")`, 서비스 레이어에서 store→owner 체인 재검증(`verifyStoreOwner`).
- 메뉴 삭제는 soft delete(`@SQLDelete`/`@SQLRestriction`) 유지. 기존 주문 참조 보존.
- **번들 기본 에셋(`/imgs/menu/*.svg`)은 절대 물리 삭제하지 않는다.** 업로드 파일만(`/imgs/menu/uploads/` 접두) 삭제 대상.
- 통합 테스트는 `IntegrationTestSupport` 상속, `fixtures.createOwner/createCustomer`, `fixtures.authHeader(user)` 사용. test 프로파일은 H2 + `file.upload-dir: ./build/test-uploads`.
- 단일 테스트 실행: `./gradlew test --tests "<FQCN>"` (working dir: `C:/Users/jaehoon/Desktop/cafeminsu`).

---

## File Structure

신규:
- `src/main/java/com/cafeminsu/global/storage/FileStorageService.java` — 파일 저장/삭제 캡슐화. URL 접두 상수 보유.
- `src/main/java/com/cafeminsu/domain/image/dto/ImageUploadRes.java` — 업로드 응답 DTO.
- `src/main/java/com/cafeminsu/domain/image/controller/ImageController.java` — 업로드 엔드포인트.
- `src/test/java/com/cafeminsu/global/storage/FileStorageServiceTest.java` — 단위 테스트.
- `src/test/java/com/cafeminsu/image/ImageFlowTest.java` — 업로드/서빙/권한 통합 테스트.
- `src/test/java/com/cafeminsu/menu/MenuImageLifecycleTest.java` — 수정/삭제 시 파일 정리 통합 테스트.

수정:
- `src/main/java/com/cafeminsu/global/common/BaseResponseStatus.java` — 에러 코드 4개 추가.
- `src/main/java/com/cafeminsu/global/exception/GlobalExceptionHandler.java` — 멀티파트 예외 핸들러 2개.
- `src/main/java/com/cafeminsu/global/config/SecurityConfig.java` — 업로드 권한 규칙.
- `src/main/java/com/cafeminsu/global/config/WebConfig.java` — 업로드 정적 서빙 핸들러.
- `src/main/java/com/cafeminsu/domain/menu/service/MenuService.java` — update/delete 시 파일 정리.
- `src/main/resources/application.yml` — `file.*`, `spring.servlet.multipart.*`.
- `src/test/resources/application-test.yml` — `file.*`.

---

## Task 1: FileStorageService + 에러 코드 + 파일 설정

**Files:**
- Create: `src/main/java/com/cafeminsu/global/storage/FileStorageService.java`
- Modify: `src/main/java/com/cafeminsu/global/common/BaseResponseStatus.java` (RESOURCE_NOT_FOUND(2004) 아래)
- Modify: `src/main/resources/application.yml` (top-level `file:` 블록 추가)
- Modify: `src/test/resources/application-test.yml` (top-level `file:` 블록 추가)
- Test: `src/test/java/com/cafeminsu/global/storage/FileStorageServiceTest.java`

**Interfaces:**
- Consumes: `BaseException`, `BaseResponseStatus`.
- Produces:
  - `FileStorageService.MENU_UPLOAD_URL_PREFIX` = `"/imgs/menu/uploads/"` (public static final String)
  - `String FileStorageService.store(MultipartFile file)` → 업로드 URL 반환
  - `void FileStorageService.delete(String imageUrl)` → 업로드 접두인 경우에만 물리 삭제
  - 생성자 `FileStorageService(String uploadDir, long maxBytes)` (Spring은 `@Value`로 주입; 테스트는 직접 호출)
  - 새 에러 코드: `EMPTY_FILE(2005)`, `UNSUPPORTED_IMAGE_TYPE(2006)`, `IMAGE_SIZE_EXCEEDED(2007)`, `FILE_STORAGE_FAILED(2008)`

- [ ] **Step 1: 에러 코드 추가**

`BaseResponseStatus.java`의 `RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, 2004, "요청한 리소스를 찾을 수 없습니다."),` 바로 아래에 추가:

```java
    EMPTY_FILE(HttpStatus.BAD_REQUEST, 2005, "업로드할 파일이 비어 있습니다."),
    UNSUPPORTED_IMAGE_TYPE(HttpStatus.BAD_REQUEST, 2006, "지원하지 않는 이미지 형식입니다. (jpg, jpeg, png, webp만 허용)"),
    IMAGE_SIZE_EXCEEDED(HttpStatus.PAYLOAD_TOO_LARGE, 2007, "이미지 용량이 허용치를 초과했습니다."),
    FILE_STORAGE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, 2008, "파일 저장에 실패했습니다."),
```

- [ ] **Step 2: 설정값 추가**

`application.yml` 맨 아래(또는 `server:` 블록과 같은 레벨)에 추가:

```yaml
# 파일 업로드
file:
  upload-dir: ${FILE_UPLOAD_DIR:./uploads}
  max-image-bytes: 5242880   # 5MB
```

`application.yml`의 기존 `spring:` 블록 안(예: `jpa:`와 같은 레벨)에 추가:

```yaml
  servlet:
    multipart:
      max-file-size: 5MB
      max-request-size: 10MB
```

`application-test.yml` 맨 아래에 추가:

```yaml
# 파일 업로드 (테스트용 디렉토리)
file:
  upload-dir: ./build/test-uploads
  max-image-bytes: 5242880
```

- [ ] **Step 3: 실패하는 단위 테스트 작성**

`src/test/java/com/cafeminsu/global/storage/FileStorageServiceTest.java`:

```java
package com.cafeminsu.global.storage;

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
        assertThatThrownBy(() -> service.store(file)).isInstanceOf(BaseException.class);
    }

    @Test
    @DisplayName("허용되지 않은 확장자는 예외")
    void badExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "x.gif", "image/gif", new byte[]{1});
        assertThatThrownBy(() -> service.store(file)).isInstanceOf(BaseException.class);
    }

    @Test
    @DisplayName("최대 용량 초과는 예외")
    void tooBig() {
        FileStorageService small = new FileStorageService(tempDir.toString(), 2L);
        MockMultipartFile file = new MockMultipartFile("file", "x.png", "image/png", new byte[]{1, 2, 3});
        assertThatThrownBy(() -> small.store(file)).isInstanceOf(BaseException.class);
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
```

- [ ] **Step 4: 테스트 실패 확인**

Run: `./gradlew test --tests "com.cafeminsu.global.storage.FileStorageServiceTest"`
Expected: 컴파일 실패 — `FileStorageService` 클래스 없음.

- [ ] **Step 5: FileStorageService 구현**

`src/main/java/com/cafeminsu/global/storage/FileStorageService.java`:

```java
package com.cafeminsu.global.storage;

import com.cafeminsu.global.common.BaseResponseStatus;
import com.cafeminsu.global.exception.BaseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

/**
 * 메뉴 이미지 파일 저장/삭제 캡슐화.
 *
 * 업로드된 파일만 {@link #MENU_UPLOAD_URL_PREFIX} 접두를 가지며,
 * 번들 기본 svg(classpath static)와 구분된다. delete()는 이 접두인 경우에만 동작.
 */
@Slf4j
@Service
public class FileStorageService {

    /** 업로드 메뉴 이미지의 서비스 URL 접두. 번들 기본 에셋과 구분하는 기준. */
    public static final String MENU_UPLOAD_URL_PREFIX = "/imgs/menu/uploads/";

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    private final Path menuDir;
    private final long maxBytes;

    public FileStorageService(
            @Value("${file.upload-dir}") String uploadDir,
            @Value("${file.max-image-bytes:5242880}") long maxBytes) {
        this.menuDir = Paths.get(uploadDir, "menu").toAbsolutePath().normalize();
        this.maxBytes = maxBytes;
    }

    /** 메뉴 이미지 저장 후 서비스 URL(`/imgs/menu/uploads/{uuid}.{ext}`) 반환. */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BaseException(BaseResponseStatus.EMPTY_FILE);
        }
        if (file.getSize() > maxBytes) {
            throw new BaseException(BaseResponseStatus.IMAGE_SIZE_EXCEEDED);
        }
        String ext = extractExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BaseException(BaseResponseStatus.UNSUPPORTED_IMAGE_TYPE);
        }
        String filename = UUID.randomUUID() + "." + ext;
        try {
            Files.createDirectories(menuDir);
            Path target = menuDir.resolve(filename);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("[FileStorage] store failed", e);
            throw new BaseException(BaseResponseStatus.FILE_STORAGE_FAILED);
        }
        return MENU_UPLOAD_URL_PREFIX + filename;
    }

    /** 업로드 파일(접두 일치)만 물리 삭제. 번들 svg 등 다른 URL은 no-op. */
    public void delete(String imageUrl) {
        if (imageUrl == null || !imageUrl.startsWith(MENU_UPLOAD_URL_PREFIX)) {
            return;
        }
        String filename = imageUrl.substring(MENU_UPLOAD_URL_PREFIX.length());
        // path traversal 방지: 순수 파일명만 허용
        if (filename.isBlank() || filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            return;
        }
        try {
            Files.deleteIfExists(menuDir.resolve(filename));
        } catch (IOException e) {
            // 삭제 실패는 치명적이지 않음. 로그만 남기고 메뉴 작업은 계속.
            log.warn("[FileStorage] delete failed: {}", filename, e);
        }
    }

    private String extractExtension(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            return "";
        }
        int dot = originalFilename.lastIndexOf('.');
        if (dot < 0 || dot == originalFilename.length() - 1) {
            return "";
        }
        return originalFilename.substring(dot + 1).toLowerCase();
    }
}
```

- [ ] **Step 6: 테스트 통과 확인**

Run: `./gradlew test --tests "com.cafeminsu.global.storage.FileStorageServiceTest"`
Expected: PASS (5개 모두).

- [ ] **Step 7: 커밋**

```bash
git add src/main/java/com/cafeminsu/global/storage/FileStorageService.java \
        src/main/java/com/cafeminsu/global/common/BaseResponseStatus.java \
        src/main/resources/application.yml \
        src/test/resources/application-test.yml \
        src/test/java/com/cafeminsu/global/storage/FileStorageServiceTest.java
git commit -m "feat: 메뉴 이미지 파일 저장/삭제 FileStorageService 추가"
```

---

## Task 2: 업로드 엔드포인트 + 권한 + 멀티파트 예외 핸들러

**Files:**
- Create: `src/main/java/com/cafeminsu/domain/image/dto/ImageUploadRes.java`
- Create: `src/main/java/com/cafeminsu/domain/image/controller/ImageController.java`
- Modify: `src/main/java/com/cafeminsu/global/config/SecurityConfig.java` (`.anyRequest().authenticated()` 바로 위)
- Modify: `src/main/java/com/cafeminsu/global/exception/GlobalExceptionHandler.java`
- Test: `src/test/java/com/cafeminsu/image/ImageFlowTest.java`

**Interfaces:**
- Consumes: `FileStorageService.store(MultipartFile)`, `BaseResponse`, `fixtures.createOwner/createCustomer/authHeader`.
- Produces:
  - `POST /api/images/menu` (multipart, 파트명 `file`) → `BaseResponse<ImageUploadRes>`
  - `ImageUploadRes(String imageUrl)` record

- [ ] **Step 1: 실패하는 통합 테스트 작성**

`src/test/java/com/cafeminsu/image/ImageFlowTest.java`:

```java
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
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests "com.cafeminsu.image.ImageFlowTest"`
Expected: 컴파일 실패 또는 404 — `ImageController` / `ImageUploadRes` 없음.

- [ ] **Step 3: ImageUploadRes DTO 작성**

`src/main/java/com/cafeminsu/domain/image/dto/ImageUploadRes.java`:

```java
package com.cafeminsu.domain.image.dto;

public record ImageUploadRes(
        String imageUrl
) {
}
```

- [ ] **Step 4: ImageController 작성**

`src/main/java/com/cafeminsu/domain/image/controller/ImageController.java`:

```java
package com.cafeminsu.domain.image.controller;

import com.cafeminsu.domain.image.dto.ImageUploadRes;
import com.cafeminsu.global.common.BaseResponse;
import com.cafeminsu.global.storage.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "10. Image", description = "이미지 업로드 API")
@RestController
@RequiredArgsConstructor
public class ImageController {

    private final FileStorageService fileStorageService;

    @Operation(summary = "메뉴 이미지 업로드",
            description = "점주만 가능. multipart/form-data, 파트명 file. 반환된 imageUrl을 메뉴 등록/수정 imageUrl에 사용.")
    @PostMapping("/api/images/menu")
    public BaseResponse<ImageUploadRes> uploadMenuImage(@RequestParam("file") MultipartFile file) {
        return BaseResponse.success(new ImageUploadRes(fileStorageService.store(file)));
    }
}
```

- [ ] **Step 5: SecurityConfig에 권한 규칙 추가**

`SecurityConfig.java`의 `.anyRequest().authenticated()` 바로 위에 추가:

```java
                        /* ===== Image 업로드 (점주 전용) ===== */
                        .requestMatchers(HttpMethod.POST, "/api/images/**").hasRole("OWNER")

```

- [ ] **Step 6: 멀티파트 예외 핸들러 추가**

`GlobalExceptionHandler.java` import 추가:

```java
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
```

`handleAccessDenied` 메서드 아래(마지막 `handleUnexpected` 위)에 추가:

```java
    /** 멀티파트 파일 용량 초과 (컨테이너 레벨 안전망) */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<BaseResponse<Void>> handleMaxUpload(MaxUploadSizeExceededException e) {
        log.warn("[MaxUpload] {}", e.getMessage());
        return ResponseEntity
                .status(BaseResponseStatus.IMAGE_SIZE_EXCEEDED.getHttpStatus())
                .body(BaseResponse.failure(BaseResponseStatus.IMAGE_SIZE_EXCEEDED));
    }

    /** 멀티파트 필수 파트(file) 누락 */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<BaseResponse<Void>> handleMissingPart(MissingServletRequestPartException e) {
        log.warn("[MissingPart] {}", e.getMessage());
        return ResponseEntity
                .status(BaseResponseStatus.INVALID_REQUEST.getHttpStatus())
                .body(BaseResponse.failure(BaseResponseStatus.INVALID_REQUEST.getCode(), e.getMessage()));
    }
```

- [ ] **Step 7: 테스트 통과 확인**

Run: `./gradlew test --tests "com.cafeminsu.image.ImageFlowTest"`
Expected: PASS (4개 모두).

- [ ] **Step 8: 커밋**

```bash
git add src/main/java/com/cafeminsu/domain/image/ \
        src/main/java/com/cafeminsu/global/config/SecurityConfig.java \
        src/main/java/com/cafeminsu/global/exception/GlobalExceptionHandler.java \
        src/test/java/com/cafeminsu/image/ImageFlowTest.java
git commit -m "feat: 메뉴 이미지 업로드 엔드포인트(POST /api/images/menu) 추가"
```

---

## Task 3: 업로드 이미지 정적 서빙 (WebConfig)

**Files:**
- Modify: `src/main/java/com/cafeminsu/global/config/WebConfig.java`
- Test: `src/test/java/com/cafeminsu/image/ImageFlowTest.java` (메서드 추가)

**Interfaces:**
- Consumes: `file.upload-dir` 설정, `FileStorageService` 업로드 결과.
- Produces: `GET /imgs/menu/uploads/{filename}` 이 외부 디렉토리에서 파일을 서빙(비로그인 OK, `/imgs/**` public).

- [ ] **Step 1: 실패하는 통합 테스트 추가**

`ImageFlowTest.java`에 import 추가:

```java
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
```

그리고 테스트 메서드 추가:

```java
    @Test
    @DisplayName("업로드한 이미지는 /imgs/menu/uploads 경로로 서빙된다")
    void uploadedFileIsServed() throws Exception {
        User owner = fixtures.createOwner("점주");

        MvcResult res = mockMvc.perform(multipart("/api/images/menu")
                        .file(pngFile())
                        .header("Authorization", fixtures.authHeader(owner)))
                .andExpect(status().isOk())
                .andReturn();
        String imageUrl = objectMapper.readTree(res.getResponse().getContentAsString())
                .at("/result/imageUrl").asText();

        mockMvc.perform(get(imageUrl))
                .andExpect(status().isOk());
    }
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests "com.cafeminsu.image.ImageFlowTest.uploadedFileIsServed"`
Expected: FAIL — 404 (resource handler 미등록).

- [ ] **Step 3: WebConfig에 리소스 핸들러 추가**

`WebConfig.java` 전체를 아래로 교체:

```java
package com.cafeminsu.global.config;

import com.cafeminsu.global.security.LoginUserIdArgumentResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final LoginUserIdArgumentResolver loginUserIdArgumentResolver;

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(loginUserIdArgumentResolver);
    }

    /**
     * 업로드된 메뉴 이미지를 외부 디렉토리(${file.upload-dir}/menu)에서 서빙.
     * 번들 기본 svg는 기존 classpath 정적 서빙(/imgs/menu/*.svg) 그대로 유지된다.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path menuDir = Paths.get(uploadDir, "menu").toAbsolutePath().normalize();
        String location = menuDir.toUri().toString();
        if (!location.endsWith("/")) {
            location += "/";
        }
        registry.addResourceHandler("/imgs/menu/uploads/**")
                .addResourceLocations(location);
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests "com.cafeminsu.image.ImageFlowTest"`
Expected: PASS (5개 모두 — 기존 4 + 신규 1).

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/cafeminsu/global/config/WebConfig.java \
        src/test/java/com/cafeminsu/image/ImageFlowTest.java
git commit -m "feat: 업로드 메뉴 이미지 정적 서빙(/imgs/menu/uploads) 추가"
```

---

## Task 4: 메뉴 삭제 시 업로드 이미지 정리

**Files:**
- Modify: `src/main/java/com/cafeminsu/domain/menu/service/MenuService.java`
- Test: `src/test/java/com/cafeminsu/menu/MenuImageLifecycleTest.java`

**Interfaces:**
- Consumes: `FileStorageService.store/delete`, `MenuService.deleteMenu`, 기존 메뉴 등록 API.
- Produces: `deleteMenu` 후 해당 메뉴의 업로드 이미지 파일 물리 삭제.

- [ ] **Step 1: 실패하는 통합 테스트 작성**

`src/test/java/com/cafeminsu/menu/MenuImageLifecycleTest.java`:

```java
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
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests "com.cafeminsu.menu.MenuImageLifecycleTest.deleteMenuRemovesUploadedImage"`
Expected: FAIL — 삭제 후에도 파일이 존재(`assertThat(...).isFalse()` 실패).

- [ ] **Step 3: MenuService에 FileStorageService 주입 + deleteMenu 수정**

`MenuService.java` import 추가:

```java
import com.cafeminsu.global.storage.FileStorageService;
```

필드 추가(기존 `private final StoreRepository storeRepository;` 아래):

```java
    private final FileStorageService fileStorageService;
```

`deleteMenu` 메서드를 아래로 교체:

```java
    @Transactional
    public void deleteMenu(Long userId, Long menuId) {
        Menu menu = findOwnedMenu(menuId, userId);
        String imageUrl = menu.getImageUrl();
        menuRepository.delete(menu);  // @SQLDelete → UPDATE deleted_at
        fileStorageService.delete(imageUrl);  // 업로드 파일만 삭제(번들 svg는 no-op)
    }
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests "com.cafeminsu.menu.MenuImageLifecycleTest"`
Expected: PASS.

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/cafeminsu/domain/menu/service/MenuService.java \
        src/test/java/com/cafeminsu/menu/MenuImageLifecycleTest.java
git commit -m "feat: 메뉴 삭제 시 업로드 이미지 파일 정리"
```

---

## Task 5: 메뉴 수정 시 이미지 교체 + 이전 파일 정리

**Files:**
- Modify: `src/main/java/com/cafeminsu/domain/menu/service/MenuService.java`
- Test: `src/test/java/com/cafeminsu/menu/MenuImageLifecycleTest.java` (메서드 추가)

**Interfaces:**
- Consumes: `MenuService.updateMenu`, `FileStorageService.delete`, 업로드/등록 helper(Task 4에서 정의).
- Produces: `updateMenu`에서 `imageUrl`이 바뀌고 이전 값이 업로드 파일이면 이전 물리 파일 삭제.

- [ ] **Step 1: 실패하는 통합 테스트 추가**

`MenuImageLifecycleTest.java`에 import 추가:

```java
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
```

테스트 메서드 추가:

```java
    @Test
    @DisplayName("메뉴 수정으로 이미지 교체 시 이전 업로드 파일은 삭제된다")
    void updateMenuReplacesAndDeletesOldImage() throws Exception {
        User owner = fixtures.createOwner("점주");
        long storeId = createStore(owner);

        String oldUrl = uploadImage(owner);
        Path oldFile = resolve(oldUrl);
        long menuId = createMenuWithImage(owner, storeId, "아메리카노", 4500, oldUrl);
        assertThat(Files.exists(oldFile)).isTrue();

        String newUrl = uploadImage(owner);
        Path newFile = resolve(newUrl);

        mockMvc.perform(patch("/api/menus/" + menuId)
                        .header("Authorization", fixtures.authHeader(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imageUrl\":\"" + newUrl + "\"}"))
                .andExpect(status().isOk());

        assertThat(Files.exists(oldFile)).isFalse();  // 이전 파일 삭제됨
        assertThat(Files.exists(newFile)).isTrue();    // 새 파일 유지
    }
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests "com.cafeminsu.menu.MenuImageLifecycleTest.updateMenuReplacesAndDeletesOldImage"`
Expected: FAIL — 이전 파일이 그대로 존재(`isFalse()` 실패).

- [ ] **Step 3: updateMenu 수정**

`MenuService.java`의 `updateMenu` 메서드를 아래로 교체:

```java
    @Transactional
    public void updateMenu(Long userId, Long menuId, MenuUpdateReq req) {
        Menu menu = findOwnedMenu(menuId, userId);
        String oldImageUrl = menu.getImageUrl();
        menu.updatePartial(
                req.name(),
                req.description(),
                req.price(),
                req.category(),
                req.imageUrl()
        );
        // 이미지가 실제로 교체된 경우에만 이전 업로드 파일 정리(번들 svg는 no-op)
        if (req.imageUrl() != null && !java.util.Objects.equals(oldImageUrl, req.imageUrl())) {
            fileStorageService.delete(oldImageUrl);
        }
    }
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests "com.cafeminsu.menu.MenuImageLifecycleTest"`
Expected: PASS (2개 모두).

- [ ] **Step 5: 전체 회귀 테스트**

Run: `./gradlew test`
Expected: 기존 테스트 포함 전부 PASS (메뉴 등록/조회 JSON 계약 회귀 없음).

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/cafeminsu/domain/menu/service/MenuService.java \
        src/test/java/com/cafeminsu/menu/MenuImageLifecycleTest.java
git commit -m "feat: 메뉴 수정 시 이미지 교체 및 이전 업로드 파일 정리"
```

---

## Self-Review

**Spec coverage:**
- 업로드 엔드포인트(4.1) → Task 2 ✓
- 파일 저장/서빙(4.2): upload-dir/멀티파트 설정 → Task 1·2, ResourceHandler → Task 3 ✓
- 등록 변경 최소(4.3) → 변경 없음(기존 JSON 그대로), Task 4·5 helper에서 검증 ✓
- 수정 교체 + 이전 파일 정리(4.4) → Task 5 ✓
- 삭제 시 이미지 삭제(4.5) → Task 4 ✓
- FileStorageService / 에러코드 / Security(4.6) → Task 1·2 ✓
- 업로드/번들 구분(접두) → Task 1 상수 + delete 가드, 단위·통합 테스트 ✓

**Placeholder scan:** 모든 step에 실제 코드/명령/기대 출력 포함. TODO·"적절히 처리" 없음. ✓

**Type consistency:** `MENU_UPLOAD_URL_PREFIX`(="/imgs/menu/uploads/"), `store`/`delete` 시그니처, `ImageUploadRes(imageUrl)`, 에러코드 2005~2008, 권한 매처 경로 `/api/images/**` 가 전 태스크에서 일치. ✓
