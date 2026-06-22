package com.cafeminsu.domain.image.controller;

import com.cafeminsu.domain.image.dto.ImageUploadRes;
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
    public ImageUploadRes uploadMenuImage(@RequestParam("file") MultipartFile file) {
        return new ImageUploadRes(fileStorageService.store(file));
    }
}
