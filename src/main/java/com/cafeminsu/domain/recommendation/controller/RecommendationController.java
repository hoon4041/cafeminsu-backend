package com.cafeminsu.domain.recommendation.controller;

import com.cafeminsu.domain.recommendation.dto.TodayRecommendationRes;
import com.cafeminsu.domain.recommendation.service.RecommendationService;
import com.cafeminsu.global.security.LoginUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "10. Recommendation", description = "메뉴 추천 API")
@RestController
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    /**
     * 오늘의 메뉴 추천 — 로그인 필요.
     *
     * 날씨·시간/계절·고객 주문 이력을 컨텍스트로 LLM이 메뉴 2건과 추천 이유를 생성한다.
     * 보안 설정상 명시 규칙이 없는 /api/** 경로라 기본적으로 인증이 강제된다.
     */
    @Operation(summary = "오늘의 메뉴 추천",
            description = "날씨·시간·주문이력을 반영한 LLM 추천 2건. menuId로 메뉴 상세를 이어 조회 가능.")
    @GetMapping("/api/stores/{storeId}/recommendations/today")
    public TodayRecommendationRes recommendToday(@LoginUserId Long userId,
                                                 @PathVariable Long storeId) {
        return recommendationService.recommendToday(userId, storeId);
    }
}
