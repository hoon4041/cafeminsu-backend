package com.cafeminsu.domain.recommendation.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

/**
 * 현재 날씨 조회 — Open-Meteo (무료, API 키 불필요).
 *
 * 위도/경도만으로 현재 기온·날씨 코드를 받아온다.
 * Spec: https://open-meteo.com/en/docs (WMO weather_code 기준)
 *
 * 날씨는 추천의 '보조' 컨텍스트라, 조회 실패해도 추천 자체는 진행되어야 한다.
 * 따라서 실패 시 예외를 던지지 않고 {@link Weather#unknown()}을 반환한다.
 */
@Slf4j
@Component
public class WeatherClient {

    private static final String BASE_URL = "https://api.open-meteo.com/v1/forecast";

    private final RestClient restClient;

    public WeatherClient() {
        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .build();
    }

    public Weather fetchCurrent(BigDecimal latitude, BigDecimal longitude) {
        try {
            JsonNode root = restClient.get()
                    .uri(uri -> uri
                            .queryParam("latitude", latitude)
                            .queryParam("longitude", longitude)
                            .queryParam("current", "temperature_2m,weather_code")
                            .queryParam("timezone", "auto")
                            .build())
                    .retrieve()
                    .body(JsonNode.class);

            if (root == null || root.path("current").isMissingNode()) {
                return Weather.unknown();
            }
            JsonNode current = root.path("current");
            double temperature = current.path("temperature_2m").asDouble();
            int weatherCode = current.path("weather_code").asInt();
            return new Weather(describe(weatherCode), temperature);
        } catch (Exception e) {
            // 날씨는 부가 정보 — 실패해도 추천은 계속 진행.
            log.warn("Weather fetch failed, proceeding without weather: {}", e.getMessage());
            return Weather.unknown();
        }
    }

    /** WMO weather interpretation code → 한국어 설명. */
    private String describe(int code) {
        return switch (code) {
            case 0 -> "맑음";
            case 1, 2 -> "대체로 맑음";
            case 3 -> "흐림";
            case 45, 48 -> "안개";
            case 51, 53, 55, 56, 57 -> "이슬비";
            case 61, 63, 65, 66, 67 -> "비";
            case 71, 73, 75, 77 -> "눈";
            case 80, 81, 82 -> "소나기";
            case 85, 86 -> "눈 소나기";
            case 95, 96, 99 -> "뇌우";
            default -> "흐림";
        };
    }

    /**
     * 현재 날씨.
     *
     * @param description 한국어 날씨 설명 (예: "맑음")
     * @param temperature 섭씨 기온. 조회 실패 시 null.
     */
    public record Weather(String description, Double temperature) {

        public static Weather unknown() {
            return new Weather("정보 없음", null);
        }

        public boolean isKnown() {
            return temperature != null;
        }
    }
}
