package com.cafeminsu.domain.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 오늘의 메뉴 추천 응답.
 *
 * 날씨·시간·주문이력을 컨텍스트로 LLM이 고른 메뉴와 추천 이유를 함께 담는다.
 * 추천은 보통 2건. 안드로이드는 menuId로 메뉴 상세를 이어서 조회할 수 있다.
 *
 * 예:
 *   {
 *     "recommendations": [
 *       { "menuId": 12, "menuName": "아이스 아메리카노", "price": 4500,
 *         "imageUrl": "...", "reason": "더운 날씨엔 시원한 아메리카노가 제격이에요." },
 *       { "menuId": 21, "menuName": "자몽에이드", "price": 5500,
 *         "imageUrl": "...", "reason": "최근 즐겨 드신 상큼한 음료 취향에 맞춰 추천드려요." }
 *     ]
 *   }
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TodayRecommendationRes(
        List<Item> recommendations
) {
    public record Item(
            Long menuId,
            String menuName,
            Integer price,
            String imageUrl,
            String reason
    ) {
    }
}
