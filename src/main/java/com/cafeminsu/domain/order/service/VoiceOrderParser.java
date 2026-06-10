package com.cafeminsu.domain.order.service;

import com.cafeminsu.domain.menu.entity.Menu;
import com.cafeminsu.domain.menu.repository.MenuRepository;
import com.cafeminsu.domain.order.dto.VoiceOrderRes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 음성 텍스트를 메뉴 주문으로 파싱.
 *
 * 현재 구현: 단순 키워드 매칭 (메뉴명이 텍스트에 포함되면 매치).
 * 향후: OpenAI GPT API로 자연어 파싱 + 옵션 추론.
 *
 * 예시:
 *   "아메리카노 두 잔" → [(아메리카노, 2)]
 *   "아메리카노 한 잔이랑 라떼 한 잔" → [(아메리카노, 1), (라떼, 1)]
 */
@Component
@RequiredArgsConstructor
public class VoiceOrderParser {

    // 매우 단순한 한국어 수량 매핑
    private static final java.util.Map<String, Integer> KOREAN_NUMBER = java.util.Map.ofEntries(
            java.util.Map.entry("한", 1),
            java.util.Map.entry("하나", 1),
            java.util.Map.entry("두", 2),
            java.util.Map.entry("둘", 2),
            java.util.Map.entry("세", 3),
            java.util.Map.entry("셋", 3),
            java.util.Map.entry("네", 4),
            java.util.Map.entry("넷", 4),
            java.util.Map.entry("다섯", 5),
            java.util.Map.entry("여섯", 6),
            java.util.Map.entry("일곱", 7),
            java.util.Map.entry("여덟", 8),
            java.util.Map.entry("아홉", 9),
            java.util.Map.entry("열", 10)
    );

    private final MenuRepository menuRepository;

    public VoiceOrderRes parse(Long storeId, String audioText) {
        if (audioText == null || audioText.isBlank()) {
            return VoiceOrderRes.failed("주문 내용을 인식하지 못했습니다.");
        }

        List<Menu> menus = menuRepository.findAllByStoreIdOrderByIdDesc(storeId);
        if (menus.isEmpty()) {
            return VoiceOrderRes.failed("매장에 등록된 메뉴가 없습니다.");
        }

        List<VoiceOrderRes.ParsedItem> matched = new ArrayList<>();
        // 더 긴 이름부터 매칭 (예: "카페라떼"가 "라떼"보다 먼저 매칭되도록)
        menus.sort((a, b) -> Integer.compare(b.getName().length(), a.getName().length()));

        for (Menu menu : menus) {
            if (!audioText.contains(menu.getName())) continue;
            if (!menu.isAvailable()) continue;

            int qty = extractQuantityNear(audioText, menu.getName());
            matched.add(new VoiceOrderRes.ParsedItem(
                    menu.getId(), menu.getName(), qty, List.of()
            ));
        }

        if (matched.isEmpty()) {
            return VoiceOrderRes.failed("메뉴를 찾지 못했습니다. 다시 말씀해주세요.");
        }
        // confidence는 단순 매칭이라 보수적으로 0.7
        return new VoiceOrderRes(matched, 0.7, null);
    }

    /** 메뉴명 근처의 숫자(아라비아·한글)를 수량으로 추출. 못 찾으면 1. */
    private int extractQuantityNear(String text, String menuName) {
        int idx = text.indexOf(menuName);
        if (idx < 0) return 1;

        // 메뉴명 뒤 30자 정도까지 둘러봄
        int end = Math.min(text.length(), idx + menuName.length() + 30);
        String window = text.substring(idx + menuName.length(), end);

        // 아라비아 숫자 우선
        Matcher m = Pattern.compile("(\\d+)").matcher(window);
        if (m.find()) {
            try {
                int n = Integer.parseInt(m.group(1));
                if (n >= 1 && n <= 99) return n;
            } catch (NumberFormatException ignore) {}
        }

        // 한국어 수량
        for (var e : KOREAN_NUMBER.entrySet()) {
            if (window.contains(e.getKey())) return e.getValue();
        }
        return 1;
    }
}
