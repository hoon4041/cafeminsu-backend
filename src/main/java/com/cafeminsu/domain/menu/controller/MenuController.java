package com.cafeminsu.domain.menu.controller;

import com.cafeminsu.domain.menu.dto.MenuAvailabilityReq;
import com.cafeminsu.domain.menu.dto.MenuCreateReq;
import com.cafeminsu.domain.menu.dto.MenuCreateRes;
import com.cafeminsu.domain.menu.dto.MenuDetailRes;
import com.cafeminsu.domain.menu.dto.MenuListItemRes;
import com.cafeminsu.domain.menu.dto.MenuOptionCreateReq;
import com.cafeminsu.domain.menu.dto.MenuOptionCreateRes;
import com.cafeminsu.domain.menu.dto.MenuOptionUpdateReq;
import com.cafeminsu.domain.menu.dto.MenuUpdateReq;
import com.cafeminsu.domain.menu.service.MenuService;
import com.cafeminsu.global.common.BaseResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "3. Menu", description = "메뉴·옵션 API")
@RestController
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    /* ===== 매장 컨텍스트 메뉴 (POST/GET) ===== */

    /* 1. 메뉴 등록 */
    @Operation(summary = "메뉴 등록", description = "점주만 가능. 매장 ID 경로 변수.")
    @PostMapping("/api/stores/{storeId}/menus")
    public BaseResponse<MenuCreateRes> create(@LoginUserId Long userId,
                                              @PathVariable Long storeId,
                                              @Valid @RequestBody MenuCreateReq req) {
        return BaseResponse.success(menuService.createMenu(userId, storeId, req));
    }

    /* 2. 매장 메뉴 목록 — 비로그인 OK */
    @SecurityRequirements
    @Operation(summary = "매장 메뉴 목록", description = "카테고리 필터 옵션. 매장 상세 화면에서 호출.")
    @GetMapping("/api/stores/{storeId}/menus")
    public BaseResponse<List<MenuListItemRes>> listByStore(@PathVariable Long storeId,
                                                          @RequestParam(required = false) String category) {
        return BaseResponse.success(menuService.getStoreMenus(storeId, category));
    }

    /* ===== 단일 메뉴 (GET/PATCH/DELETE) ===== */

    /* 3. 메뉴 상세 (옵션 포함) — 비로그인 OK */
    @SecurityRequirements
    @Operation(summary = "메뉴 상세 (옵션 포함)", description = "주문 화면에서 호출.")
    @GetMapping("/api/menus/{menuId}")
    public BaseResponse<MenuDetailRes> detail(@PathVariable Long menuId) {
        return BaseResponse.success(menuService.getMenuDetail(menuId));
    }

    /* 4. 메뉴 수정 */
    @Operation(summary = "메뉴 수정", description = "점주 본인만. 수정할 필드만 전달.")
    @PatchMapping("/api/menus/{menuId}")
    public BaseResponse<Void> update(@LoginUserId Long userId,
                                     @PathVariable Long menuId,
                                     @Valid @RequestBody MenuUpdateReq req) {
        menuService.updateMenu(userId, menuId, req);
        return BaseResponse.success();
    }

    /* 5. 메뉴 삭제 (soft) */
    @Operation(summary = "메뉴 삭제", description = "Soft delete. 기존 주문 참조 유지.")
    @DeleteMapping("/api/menus/{menuId}")
    public BaseResponse<Void> delete(@LoginUserId Long userId,
                                     @PathVariable Long menuId) {
        menuService.deleteMenu(userId, menuId);
        return BaseResponse.success();
    }

    /* 6. 판매 가능 토글 */
    @Operation(summary = "판매 가능 토글", description = "재료 소진 시 점주가 빠르게 끔/켬.")
    @PatchMapping("/api/menus/{menuId}/availability")
    public BaseResponse<Void> changeAvailability(@LoginUserId Long userId,
                                                 @PathVariable Long menuId,
                                                 @Valid @RequestBody MenuAvailabilityReq req) {
        menuService.changeAvailability(userId, menuId, req.isAvailable());
        return BaseResponse.success();
    }

    /* ===== 메뉴 옵션 ===== */

    /* 7. 메뉴 옵션 추가 */
    @Operation(summary = "메뉴 옵션 추가",
            description = "optionGroup 예: size, temp, shot, syrup")
    @PostMapping("/api/menus/{menuId}/options")
    public BaseResponse<MenuOptionCreateRes> addOption(@LoginUserId Long userId,
                                                      @PathVariable Long menuId,
                                                      @Valid @RequestBody MenuOptionCreateReq req) {
        return BaseResponse.success(menuService.addOption(userId, menuId, req));
    }

    /* 8. 옵션 수정 */
    @Operation(summary = "메뉴 옵션 수정")
    @PatchMapping("/api/menu-options/{optionId}")
    public BaseResponse<Void> updateOption(@LoginUserId Long userId,
                                           @PathVariable Long optionId,
                                           @Valid @RequestBody MenuOptionUpdateReq req) {
        menuService.updateOption(userId, optionId, req);
        return BaseResponse.success();
    }

    /* 9. 옵션 삭제 */
    @Operation(summary = "메뉴 옵션 삭제")
    @DeleteMapping("/api/menu-options/{optionId}")
    public BaseResponse<Void> deleteOption(@LoginUserId Long userId,
                                           @PathVariable Long optionId) {
        menuService.deleteOption(userId, optionId);
        return BaseResponse.success();
    }
}
