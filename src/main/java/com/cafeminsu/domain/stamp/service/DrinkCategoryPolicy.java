package com.cafeminsu.domain.stamp.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 스탬프 적립 대상('음료') 판별 정책.
 *
 * 메뉴 category는 자유 문자열이라 코드로 음료를 단정할 수 없다.
 * 따라서 음료로 인정할 카테고리 목록을 설정(stamp.drink-categories)으로 관리하고,
 * 대소문자·앞뒤 공백을 무시하고 비교한다.
 *
 * 설정 예) application.yml
 *   stamp:
 *     drink-categories: 커피,음료,에이드,티,스무디,라떼
 */
@Component
public class DrinkCategoryPolicy {

    private final Set<String> drinkCategories;

    public DrinkCategoryPolicy(@Value("${stamp.drink-categories}") List<String> categories) {
        this.drinkCategories = categories.stream()
                .filter(Objects::nonNull)
                .map(DrinkCategoryPolicy::normalize)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    /** 해당 카테고리가 음료(스탬프 적립 대상)인지. */
    public boolean isDrink(String category) {
        if (category == null) return false;
        return drinkCategories.contains(normalize(category));
    }

    private static String normalize(String s) {
        return s.trim().toLowerCase();
    }
}
