package com.cafeminsu.domain.store.service;

import com.cafeminsu.domain.store.dto.MyStoreRes;
import com.cafeminsu.domain.store.dto.NearbyStoreRes;
import com.cafeminsu.domain.store.dto.StoreCreateReq;
import com.cafeminsu.domain.store.dto.StoreCreateRes;
import com.cafeminsu.domain.store.dto.StoreDetailRes;
import com.cafeminsu.domain.store.dto.StoreSearchRes;
import com.cafeminsu.domain.store.dto.StoreUpdateReq;
import com.cafeminsu.domain.store.entity.Store;
import com.cafeminsu.domain.store.repository.StoreRepository;
import com.cafeminsu.domain.user.entity.User;
import com.cafeminsu.domain.user.entity.UserRole;
import com.cafeminsu.domain.user.repository.UserRepository;
import com.cafeminsu.global.common.BaseResponseStatus;
import com.cafeminsu.global.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreService {

    /** 주변 매장 검색 반경 안전 한계 (10km) */
    private static final double MAX_RADIUS_METERS = 10_000.0;

    private final StoreRepository storeRepository;
    private final UserRepository userRepository;

    /* =========================================================
     * 1) 매장 등록
     * ========================================================= */
    @Transactional
    public StoreCreateRes createStore(Long userId, StoreCreateReq req) {
        // OWNER 권한 재확인 (SecurityConfig에서 이미 막지만 이중 검증)
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.USER_NOT_FOUND));
        if (owner.getRole() != UserRole.OWNER) {
            throw new BaseException(BaseResponseStatus.NOT_AN_OWNER);
        }

        Store store = Store.builder()
                .ownerId(userId)
                .name(req.name())
                .address(req.address())
                .latitude(req.latitude())
                .longitude(req.longitude())
                .phone(req.phone())
                .businessHours(req.businessHours())
                .imageUrl(req.imageUrl())
                .build();

        Store saved = storeRepository.save(store);
        return new StoreCreateRes(saved.getId());
    }

    /* =========================================================
     * 2) 매장 목록 (검색)
     * ========================================================= */
    public StoreSearchRes searchStores(String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Store> result = StringUtils.hasText(keyword)
                ? storeRepository.searchByKeyword(keyword, pageable)
                : storeRepository.findAllByOrderByIdDesc(pageable);
        return StoreSearchRes.from(result);
    }

    /* =========================================================
     * 3) 주변 매장
     * ========================================================= */
    public List<NearbyStoreRes> findNearby(BigDecimal latitude, BigDecimal longitude, double radiusMeters) {
        // 너무 큰 반경은 거절 — DB 부하 보호
        double safeRadius = Math.min(radiusMeters, MAX_RADIUS_METERS);
        return storeRepository.findNearby(latitude, longitude, safeRadius).stream()
                .map(NearbyStoreRes::from)
                .toList();
    }

    /* =========================================================
     * 4) 매장 상세
     * ========================================================= */
    public StoreDetailRes getDetail(Long storeId) {
        return StoreDetailRes.from(findStoreOrThrow(storeId));
    }

    /* =========================================================
     * 5) 매장 정보 수정
     * ========================================================= */
    @Transactional
    public void updateStore(Long userId, Long storeId, StoreUpdateReq req) {
        Store store = findOwnedStore(storeId, userId);
        store.updatePartial(
                req.name(),
                req.address(),
                req.phone(),
                req.businessHours(),
                req.imageUrl()
        );
    }

    /* =========================================================
     * 6) 매장 폐점 (soft delete)
     * ========================================================= */
    @Transactional
    public void deleteStore(Long userId, Long storeId) {
        Store store = findOwnedStore(storeId, userId);
        storeRepository.delete(store);  // @SQLDelete가 UPDATE로 변환
    }

    /* =========================================================
     * 7) 내 매장 목록
     * ========================================================= */
    public List<MyStoreRes> getMyStores(Long userId) {
        return storeRepository.findAllByOwnerIdOrderByIdDesc(userId).stream()
                .map(MyStoreRes::from)
                .toList();
    }

    /* ============================
     * helpers
     * ============================ */
    private Store findStoreOrThrow(Long storeId) {
        return storeRepository.findById(storeId)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.STORE_NOT_FOUND));
    }

    /** 매장 존재 + 본인 소유 확인 */
    private Store findOwnedStore(Long storeId, Long userId) {
        Store store = findStoreOrThrow(storeId);
        if (!store.isOwnedBy(userId)) {
            throw new BaseException(BaseResponseStatus.NOT_STORE_OWNER);
        }
        return store;
    }
}
