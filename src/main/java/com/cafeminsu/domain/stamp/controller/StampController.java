package com.cafeminsu.domain.stamp.controller;

import com.cafeminsu.domain.stamp.dto.StampDetailRes;
import com.cafeminsu.domain.stamp.dto.StampSummaryRes;
import com.cafeminsu.domain.stamp.service.StampService;
import com.cafeminsu.global.security.LoginUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "7. Stamp", description = "스탬프 API")
@RestController
@RequestMapping("/api/stamps")
@RequiredArgsConstructor
public class StampController {

    private final StampService stampService;

    /* 1. 내 스탬프 목록 — 매장별 누적 */
    @Operation(summary = "내 스탬프 목록")
    @GetMapping
    public List<StampSummaryRes> myStamps(@LoginUserId Long userId) {
        return stampService.getMyStamps(userId);
    }

    /* 2. 특정 매장 스탬프 + 적립 이력 */
    @Operation(summary = "특정 매장 스탬프 상세", description = "적립 이력 포함")
    @GetMapping("/{storeId}")
    public StampDetailRes storeStamp(@LoginUserId Long userId,
                                     @PathVariable Long storeId) {
        return stampService.getStoreStamp(userId, storeId);
    }
}
