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
