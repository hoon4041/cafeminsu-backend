package com.cafeminsu.domain.stamp.service;

import com.cafeminsu.domain.stamp.dto.StampDetailRes;
import com.cafeminsu.domain.stamp.dto.StampSummaryRes;
import com.cafeminsu.domain.stamp.entity.Stamp;
import com.cafeminsu.domain.stamp.entity.StampHistory;
import com.cafeminsu.domain.stamp.repository.StampHistoryRepository;
import com.cafeminsu.domain.stamp.repository.StampRepository;
import com.cafeminsu.domain.store.entity.Store;
import com.cafeminsu.domain.store.repository.StoreRepository;
import com.cafeminsu.global.common.BaseResponseStatus;
import com.cafeminsu.global.exception.BaseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StampService {

    /** 주문 1건당 적립할 스탬프 수 (MVP: 고정 1) */
    private static final int STAMPS_PER_ORDER = 1;

    private final StampRepository stampRepository;
    private final StampHistoryRepository historyRepository;
    private final StoreRepository storeRepository;

    /**
     * 주문 완료(DONE) 시 OrderService에서 직접 호출.
     * Stamp row 없으면 생성, 있으면 increment.
     */
    @Transactional
    public void earnFromOrder(Long userId, Long storeId, Long orderId) {
        Stamp stamp = stampRepository.findByUserIdAndStoreId(userId, storeId)
                .orElseGet(() -> stampRepository.save(
                        Stamp.builder().userId(userId).storeId(storeId).build()
                ));
        stamp.earn(STAMPS_PER_ORDER);

        historyRepository.save(StampHistory.builder()
                .stampId(stamp.getId())
                .orderId(orderId)
                .earnedCount(STAMPS_PER_ORDER)
                .build());

        log.info("[Stamp] earn user={} store={} order={} total={}",
                userId, storeId, orderId, stamp.getCount());
    }

    /** 내 모든 매장 스탬프 목록 */
    public List<StampSummaryRes> getMyStamps(Long userId) {
        List<Stamp> stamps = stampRepository.findAllByUserIdOrderByIdDesc(userId);
        Map<Long, String> storeNames = storeNames(stamps.stream().map(Stamp::getStoreId).toList());
        return stamps.stream()
                .map(s -> StampSummaryRes.of(s, storeNames.getOrDefault(s.getStoreId(), "(삭제된 매장)")))
                .toList();
    }

    /** 특정 매장 스탬프 + 적립 이력 */
    public StampDetailRes getStoreStamp(Long userId, Long storeId) {
        Stamp stamp = stampRepository.findByUserIdAndStoreId(userId, storeId)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.STAMP_NOT_FOUND));
        String storeName = storeRepository.findById(storeId)
                .map(Store::getName).orElse("(삭제된 매장)");
        List<StampHistory> histories = historyRepository.findAllByStampIdOrderByIdDesc(stamp.getId());
        return StampDetailRes.of(stamp, storeName, histories);
    }

    private Map<Long, String> storeNames(List<Long> storeIds) {
        if (storeIds.isEmpty()) return Map.of();
        return storeRepository.findAllById(new HashSet<>(storeIds)).stream()
                .collect(Collectors.toMap(Store::getId, Store::getName));
    }
}
