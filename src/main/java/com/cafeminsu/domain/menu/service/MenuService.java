package com.cafeminsu.domain.menu.service;

import com.cafeminsu.domain.menu.dto.MenuCreateReq;
import com.cafeminsu.domain.menu.dto.MenuCreateRes;
import com.cafeminsu.domain.menu.dto.MenuDetailRes;
import com.cafeminsu.domain.menu.dto.MenuListItemRes;
import com.cafeminsu.domain.menu.dto.MenuOptionCreateReq;
import com.cafeminsu.domain.menu.dto.MenuOptionCreateRes;
import com.cafeminsu.domain.menu.dto.MenuOptionUpdateReq;
import com.cafeminsu.domain.menu.dto.MenuUpdateReq;
import com.cafeminsu.domain.menu.entity.Menu;
import com.cafeminsu.domain.menu.entity.MenuOption;
import com.cafeminsu.domain.menu.repository.MenuOptionRepository;
import com.cafeminsu.domain.menu.repository.MenuRepository;
import com.cafeminsu.domain.store.entity.Store;
import com.cafeminsu.domain.store.repository.StoreRepository;
import com.cafeminsu.global.common.BaseResponseStatus;
import com.cafeminsu.global.exception.BaseException;
import com.cafeminsu.global.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MenuService {

    private final MenuRepository menuRepository;
    private final MenuOptionRepository menuOptionRepository;
    private final StoreRepository storeRepository;
    private final FileStorageService fileStorageService;

    /* =========================================================
     * 1) 메뉴 등록 (점주)
     * ========================================================= */
    @Transactional
    public MenuCreateRes createMenu(Long userId, Long storeId, MenuCreateReq req) {
        verifyStoreOwner(storeId, userId);

        Menu menu = Menu.builder()
                .storeId(storeId)
                .name(req.name())
                .description(req.description())
                .price(req.price())
                .category(req.category())
                .imageUrl(req.imageUrl())
                .isAvailable(req.isAvailable())
                .build();
        Menu saved = menuRepository.save(menu);
        return new MenuCreateRes(saved.getId());
    }

    /* =========================================================
     * 2) 매장 메뉴 목록 (카테고리 필터 옵션)
     * ========================================================= */
    public List<MenuListItemRes> getStoreMenus(Long storeId, String category) {
        // 매장 존재 확인 (없으면 404)
        if (!storeRepository.existsById(storeId)) {
            throw new BaseException(BaseResponseStatus.STORE_NOT_FOUND);
        }
        List<Menu> menus = StringUtils.hasText(category)
                ? menuRepository.findAllByStoreIdAndCategoryOrderByIdDesc(storeId, category)
                : menuRepository.findAllByStoreIdOrderByIdDesc(storeId);
        return menus.stream().map(MenuListItemRes::from).toList();
    }

    /* =========================================================
     * 3) 메뉴 상세 (옵션 포함)
     * ========================================================= */
    public MenuDetailRes getMenuDetail(Long menuId) {
        Menu menu = findMenuOrThrow(menuId);
        List<MenuOption> options = menuOptionRepository
                .findAllByMenuIdOrderByOptionGroupAscIdAsc(menuId);
        return MenuDetailRes.from(menu, options);
    }

    /* =========================================================
     * 4) 메뉴 수정 (점주)
     * ========================================================= */
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

    /* =========================================================
     * 5) 메뉴 삭제 (점주, soft)
     * ========================================================= */
    @Transactional
    public void deleteMenu(Long userId, Long menuId) {
        Menu menu = findOwnedMenu(menuId, userId);
        String imageUrl = menu.getImageUrl();
        menuRepository.delete(menu);  // @SQLDelete → UPDATE deleted_at
        fileStorageService.delete(imageUrl);  // 업로드 파일만 삭제(번들 svg는 no-op)
    }

    /* =========================================================
     * 6) 판매 가능 토글 (점주)
     * ========================================================= */
    @Transactional
    public void changeAvailability(Long userId, Long menuId, boolean isAvailable) {
        Menu menu = findOwnedMenu(menuId, userId);
        menu.changeAvailability(isAvailable);
    }

    /* =========================================================
     * 7) 메뉴 옵션 추가 (점주)
     * ========================================================= */
    @Transactional
    public MenuOptionCreateRes addOption(Long userId, Long menuId, MenuOptionCreateReq req) {
        findOwnedMenu(menuId, userId);   // 소유권 검증만, 결과는 쓰지 않음

        MenuOption option = MenuOption.builder()
                .menuId(menuId)
                .optionGroup(req.optionGroup())
                .optionName(req.optionName())
                .additionalPrice(req.additionalPrice())
                .isDefault(req.isDefault())
                .build();
        MenuOption saved = menuOptionRepository.save(option);
        return new MenuOptionCreateRes(saved.getId());
    }

    /* =========================================================
     * 8) 옵션 수정 (점주)
     * ========================================================= */
    @Transactional
    public void updateOption(Long userId, Long optionId, MenuOptionUpdateReq req) {
        MenuOption option = findOwnedOption(optionId, userId);
        option.updatePartial(
                req.optionGroup(),
                req.optionName(),
                req.additionalPrice(),
                req.isDefault()
        );
    }

    /* =========================================================
     * 9) 옵션 삭제 (점주, hard delete)
     * ========================================================= */
    @Transactional
    public void deleteOption(Long userId, Long optionId) {
        MenuOption option = findOwnedOption(optionId, userId);
        menuOptionRepository.delete(option);
    }

    /* ============================
     * helpers — 소유권 체인 검증
     * ============================ */
    private Menu findMenuOrThrow(Long menuId) {
        return menuRepository.findById(menuId)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.MENU_NOT_FOUND));
    }

    /** menu → store → owner 체인 검증. 본인 매장의 메뉴만 손댈 수 있도록. */
    private Menu findOwnedMenu(Long menuId, Long userId) {
        Menu menu = findMenuOrThrow(menuId);
        verifyStoreOwner(menu.getStoreId(), userId);
        return menu;
    }

    /** option → menu → store → owner 체인 검증. */
    private MenuOption findOwnedOption(Long optionId, Long userId) {
        MenuOption option = menuOptionRepository.findById(optionId)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.MENU_OPTION_NOT_FOUND));
        Menu menu = findMenuOrThrow(option.getMenuId());
        verifyStoreOwner(menu.getStoreId(), userId);
        return option;
    }

    /** 매장 존재 + 본인 매장인지 */
    private void verifyStoreOwner(Long storeId, Long userId) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.STORE_NOT_FOUND));
        if (!store.isOwnedBy(userId)) {
            throw new BaseException(BaseResponseStatus.NOT_STORE_OWNER);
        }
    }
}
