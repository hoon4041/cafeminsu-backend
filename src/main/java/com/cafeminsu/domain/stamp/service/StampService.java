package com.cafeminsu.domain.stamp.service;

import com.cafeminsu.domain.stamp.dto.StampDetailRes;
import com.cafeminsu.domain.stamp.dto.StampSummaryRes;
import com.cafeminsu.domain.stamp.entity.Stamp;
import com.cafeminsu.domain.stamp.entity.StampHistory;
import com.cafeminsu.domain.gifticon.service.GifticonService;
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

    /** 보상 1건으로 전환되는 스탬프 수 (10개 모이면 기프티콘 발급) */
    private static final int STAMPS_PER_REWARD = 10;

    private final StampRepository stampRepository;
    private final StampHistoryRepository historyRepository;
    private final StoreRepository storeRepository;
    private final GifticonService gifticonService;

    /**
     * 주문 완료(DONE) 시 OrderService에서 직접 호출.
     * 음료 수량만큼 적립하고, 누적이 {@value #STAMPS_PER_REWARD}개에 도달할 때마다
     * 보상 기프티콘을 발급하며 그만큼 차감한다(한 번에 여러 장도 발급 가능).
     *
     * @param stampCount 이번 주문의 음료 수량(= 적립할 스탬프 수). 0이면 적립 없음.
     */
    @Transactional
    public void earnFromOrder(Long userId, Long storeId, Long orderId, int stampCount) {
        if (stampCount <= 0) {
            return;
        }
        Stamp stamp = stampRepository.findByUserIdAndStoreId(userId, storeId)
                .orElseGet(() -> stampRepository.save(
                        Stamp.builder().userId(userId).storeId(storeId).build()
                ));
        stamp.earn(stampCount);

        historyRepository.save(StampHistory.builder()
                .stampId(stamp.getId())
                .orderId(orderId)
                .earnedCount(stampCount)
                .build());

        // 10개 단위로 보상 기프티콘 발급 + 차감
        int rewardsIssued = 0;
        while (stamp.getCount() >= STAMPS_PER_REWARD) {
            gifticonService.issueStampReward(userId);
            stamp.redeem(STAMPS_PER_REWARD);
            rewardsIssued++;
        }

        log.info("[Stamp] earn user={} store={} order={} +{} remain={} rewards={}",
                userId, storeId, orderId, stampCount, stamp.getCount(), rewardsIssued);
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
