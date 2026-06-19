package com.cafeminsu.domain.recommendation.service;

import com.cafeminsu.domain.menu.entity.Menu;
import com.cafeminsu.domain.menu.repository.MenuRepository;
import com.cafeminsu.domain.order.entity.Order;
import com.cafeminsu.domain.order.entity.OrderItem;
import com.cafeminsu.domain.order.repository.OrderRepository;
import com.cafeminsu.domain.recommendation.dto.TodayRecommendationRes;
import com.cafeminsu.domain.recommendation.service.OpenAiRecommendationClient.MenuPick;
import com.cafeminsu.domain.recommendation.service.WeatherClient.Weather;
import com.cafeminsu.domain.store.entity.Store;
import com.cafeminsu.domain.store.repository.StoreRepository;
import com.cafeminsu.global.common.BaseResponseStatus;
import com.cafeminsu.global.exception.BaseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 오늘의 메뉴 추천 오케스트레이션.
 *
 * 컨텍스트(메뉴 목록 · 주문 이력 · 시간/계절 · 날씨)를 모아 프롬프트를 구성하고,
 * OpenAI가 고른 메뉴를 실제 메뉴와 대조·검증해 응답을 만든다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    /** 추천 메뉴 개수. */
    private static final int RECOMMEND_COUNT = 2;
    /** 취향 추론에 참고할 최근 주문 건수. */
    private static final int HISTORY_ORDER_LIMIT = 10;
    /** 매장 좌표가 없을 때 날씨 조회에 쓰는 기본 좌표(구미). */
    private static final BigDecimal DEFAULT_LATITUDE = new BigDecimal("36.1085");
    private static final BigDecimal DEFAULT_LONGITUDE = new BigDecimal("128.4182");

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final MenuRepository menuRepository;
    private final OrderRepository orderRepository;
    private final StoreRepository storeRepository;
    private final WeatherClient weatherClient;
    private final OpenAiRecommendationClient openAiClient;

    @Transactional(readOnly = true)
    public TodayRecommendationRes recommendToday(Long userId, Long storeId) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.STORE_NOT_FOUND));

        List<Menu> menus = menuRepository.findAllByStoreIdOrderByIdDesc(storeId).stream()
                .filter(Menu::isAvailable)
                .toList();
        if (menus.isEmpty()) {
            throw new BaseException(BaseResponseStatus.NO_MENU_TO_RECOMMEND);
        }

        // ===== 컨텍스트 수집 =====
        LocalDateTime now = LocalDateTime.now();
        Weather weather = weatherClient.fetchCurrent(
                store.getLatitude() != null ? store.getLatitude() : DEFAULT_LATITUDE,
                store.getLongitude() != null ? store.getLongitude() : DEFAULT_LONGITUDE);
        List<String> recentMenus = recentMenuNames(userId);

        // ===== LLM 호출 =====
        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(menus, now, weather, recentMenus);
        List<MenuPick> picks = openAiClient.recommend(systemPrompt, userPrompt);

        // ===== 검증 + 응답 조립 (존재하지 않는 menuId는 버림) =====
        Map<Long, Menu> menuById = new LinkedHashMap<>();
        menus.forEach(m -> menuById.put(m.getId(), m));

        List<TodayRecommendationRes.Item> items = picks.stream()
                .map(pick -> {
                    Menu menu = menuById.get(pick.menuId());
                    if (menu == null) {
                        log.warn("OpenAI가 존재하지 않는 menuId={}를 추천하여 제외", pick.menuId());
                        return null;
                    }
                    return new TodayRecommendationRes.Item(
                            menu.getId(), menu.getName(), menu.getPrice(),
                            menu.getImageUrl(), pick.reason());
                })
                .filter(Objects::nonNull)
                .distinct()
                .limit(RECOMMEND_COUNT)
                .toList();

        if (items.isEmpty()) {
            throw new BaseException(BaseResponseStatus.RECOMMENDATION_FAILED);
        }
        return new TodayRecommendationRes(items);
    }

    /** 사용자의 최근 주문에서 메뉴명을 추출(중복 제거). 이력이 없으면 빈 리스트. */
    private List<String> recentMenuNames(Long userId) {
        List<Order> orders = orderRepository
                .findByUserIdOrderByIdDesc(userId, PageRequest.of(0, HISTORY_ORDER_LIMIT))
                .getContent();

        List<Long> menuIds = orders.stream()
                .flatMap(order -> order.getItems().stream())
                .map(OrderItem::getMenuId)
                .distinct()
                .toList();
        if (menuIds.isEmpty()) {
            return List.of();
        }
        return menuRepository.findAllById(menuIds).stream()
                .map(Menu::getName)
                .toList();
    }

    private String buildSystemPrompt() {
        return """
                당신은 카페의 메뉴 큐레이터입니다.
                주어진 컨텍스트(날씨, 시간/계절, 고객의 최근 주문 이력)를 고려해
                후보 메뉴 중에서 오늘 손님에게 어울리는 메뉴를 정확히 %d개 추천하세요.

                규칙:
                - 반드시 후보 메뉴 목록에 있는 menuId 중에서만 고르세요. 목록에 없는 메뉴를 지어내지 마세요.
                - 서로 다른 메뉴 %d개를 추천하세요.
                - 각 메뉴마다 추천 이유를 한국어로 1~2문장, 친근한 말투로 작성하세요.
                - 이유에는 날씨/시간/취향 같은 맥락을 자연스럽게 녹여주세요.

                응답은 반드시 다음 JSON 형식만 출력하세요:
                {"recommendations":[{"menuId":<숫자>,"reason":"<추천 이유>"}]}
                """.formatted(RECOMMEND_COUNT, RECOMMEND_COUNT);
    }

    private String buildUserPrompt(List<Menu> menus, LocalDateTime now,
                                   Weather weather, List<String> recentMenus) {
        StringBuilder sb = new StringBuilder();

        sb.append("[현재 상황]\n");
        sb.append("- 날짜/시간: ").append(now.format(DATE_FORMAT))
                .append(" (").append(koreanDayOfWeek(now.getDayOfWeek())).append("), ")
                .append(timeOfDay(now.getHour())).append('\n');
        sb.append("- 계절: ").append(season(now.getMonthValue())).append('\n');
        if (weather.isKnown()) {
            sb.append("- 날씨: ").append(weather.description())
                    .append(", 기온 ").append(Math.round(weather.temperature())).append("도\n");
        } else {
            sb.append("- 날씨: 정보 없음\n");
        }

        sb.append("\n[고객 최근 주문 이력]\n");
        if (recentMenus.isEmpty()) {
            sb.append("- 주문 이력 없음 (신규 또는 첫 방문 고객)\n");
        } else {
            sb.append("- ").append(String.join(", ", recentMenus)).append('\n');
        }

        sb.append("\n[후보 메뉴 목록]\n");
        for (Menu menu : menus) {
            sb.append("- [").append(menu.getId()).append("] ")
                    .append(menu.getName())
                    .append(" (").append(menu.getPrice()).append("원");
            if (menu.getCategory() != null && !menu.getCategory().isBlank()) {
                sb.append(", ").append(menu.getCategory());
            }
            sb.append(')');
            if (menu.getDescription() != null && !menu.getDescription().isBlank()) {
                sb.append(" - ").append(menu.getDescription());
            }
            sb.append('\n');
        }

        sb.append("\n위 컨텍스트를 고려해 후보 메뉴 중 ").append(RECOMMEND_COUNT)
                .append("개를 골라 추천 이유와 함께 JSON으로만 답하세요.");
        return sb.toString();
    }

    private String koreanDayOfWeek(DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case MONDAY -> "월요일";
            case TUESDAY -> "화요일";
            case WEDNESDAY -> "수요일";
            case THURSDAY -> "목요일";
            case FRIDAY -> "금요일";
            case SATURDAY -> "토요일";
            case SUNDAY -> "일요일";
        };
    }

    private String timeOfDay(int hour) {
        if (hour < 6) return "새벽";
        if (hour < 11) return "아침";
        if (hour < 14) return "점심";
        if (hour < 18) return "오후";
        if (hour < 21) return "저녁";
        return "밤";
    }

    private String season(int month) {
        return switch (month) {
            case 3, 4, 5 -> "봄";
            case 6, 7, 8 -> "여름";
            case 9, 10, 11 -> "가을";
            default -> "겨울";
        };
    }
}
