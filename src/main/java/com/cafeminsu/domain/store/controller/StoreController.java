package com.cafeminsu.domain.store.controller;

import com.cafeminsu.domain.store.dto.MyStoreRes;
import com.cafeminsu.domain.store.dto.NearbyStoreRes;
import com.cafeminsu.domain.store.dto.StoreCreateReq;
import com.cafeminsu.domain.store.dto.StoreCreateRes;
import com.cafeminsu.domain.store.dto.StoreDetailRes;
import com.cafeminsu.domain.store.dto.StoreSearchRes;
import com.cafeminsu.domain.store.dto.StoreUpdateReq;
import com.cafeminsu.domain.store.service.StoreService;
import com.cafeminsu.global.security.LoginUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@Tag(name = "2. Store", description = "매장 API")
@RestController
@RequestMapping("/api/stores")
@RequiredArgsConstructor
public class StoreController {

    private final StoreService storeService;

    /* 1. 매장 등록 (점주 권한 필요 — SecurityConfig에서 차단) */
    @Operation(summary = "매장 등록", description = "점주 권한 필요. 한 점주가 여러 매장을 운영할 수 있습니다.")
    @PostMapping
    public StoreCreateRes create(@LoginUserId Long userId,
                                 @Valid @RequestBody StoreCreateReq req) {
        return storeService.createStore(userId, req);
    }

    /* 2. 매장 목록 (키워드 검색, 페이지네이션) — 비로그인 OK */
    @SecurityRequirements
    @Operation(summary = "매장 목록·검색", description = "이름·주소 키워드. keyword 없으면 전체 목록.")
    @GetMapping
    public StoreSearchRes search(@RequestParam(required = false) String keyword,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "20") int size) {
        return storeService.searchStores(keyword, page, size);
    }

    /* 3. 주변 매장 — 비로그인 OK */
    @SecurityRequirements
    @Operation(summary = "주변 매장 검색",
            description = "위경도 기준 반경(미터) 내 매장. 거리순 정렬. 최대 반경 10km.")
    @GetMapping("/nearby")
    public List<NearbyStoreRes> nearby(
            @RequestParam BigDecimal latitude,
            @RequestParam BigDecimal longitude,
            @RequestParam(defaultValue = "1000") double radius) {
        return storeService.findNearby(latitude, longitude, radius);
    }

    /* 4. 내 매장 (점주) —
     * 주의: 경로 충돌 방지를 위해 /my를 /{storeId}보다 위에 둠.
     * Spring은 정적 경로(/my)를 패턴(/{storeId})보다 우선 매칭하긴 하지만 가독성 차원에서 위로. */
    @Operation(summary = "내가 운영하는 매장 목록", description = "매장 앱 진입 시 호출.")
    @GetMapping("/my")
    public List<MyStoreRes> myStores(@LoginUserId Long userId) {
        return storeService.getMyStores(userId);
    }

    /* 5. 매장 상세 — 비로그인 OK */
    @SecurityRequirements
    @Operation(summary = "매장 상세")
    @GetMapping("/{storeId}")
    public StoreDetailRes detail(@PathVariable Long storeId) {
        return storeService.getDetail(storeId);
    }

    /* 6. 매장 정보 수정 (점주 본인) */
    @Operation(summary = "매장 정보 수정", description = "점주 본인만 가능. 수정할 필드만 전달(부분 수정).")
    @PatchMapping("/{storeId}")
    public void update(@LoginUserId Long userId,
                       @PathVariable Long storeId,
                       @Valid @RequestBody StoreUpdateReq req) {
        storeService.updateStore(userId, storeId, req);
    }

    /* 7. 매장 폐점 (soft delete, 점주 본인) */
    @Operation(summary = "매장 폐점", description = "Soft delete. 점주 본인만 가능.")
    @DeleteMapping("/{storeId}")
    public void delete(@LoginUserId Long userId,
                       @PathVariable Long storeId) {
        storeService.deleteStore(userId, storeId);
    }
}
