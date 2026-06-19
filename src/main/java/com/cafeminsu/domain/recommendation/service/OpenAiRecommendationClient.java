package com.cafeminsu.domain.recommendation.service;

import com.cafeminsu.global.common.BaseResponseStatus;
import com.cafeminsu.global.exception.BaseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Chat Completions 호출 클라이언트 (메뉴 추천 전용).
 *
 * application.yml의 openai.api-key / openai.model 사용.
 * 응답은 JSON object 모드(response_format)로 강제해서 파싱 안정성을 높인다.
 *
 * 모델에는 후보 메뉴의 id만 제공하고 그중에서 고르게 하며,
 * 반환된 menuId는 서비스 레이어에서 실제 메뉴와 대조해 검증한다(환각 방지).
 */
@Slf4j
@Component
public class OpenAiRecommendationClient {

    private static final double TEMPERATURE = 0.7;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String model;

    /**
     * base-url은 SSAFY GMS 프록시(투명 프록시) 경유를 기본값으로 둔다.
     * GMS는 실제 OpenAI URL의 host 앞에 gms.ssafy.io/gmsapi/ 를 붙이는 방식이라
     * Chat Completions 경로(/v1/chat/completions)가 그대로 동작한다.
     * 정식 OpenAI로 바꾸려면 openai.base-url(또는 OPENAI_BASE_URL)만 교체하면 된다.
     */
    public OpenAiRecommendationClient(
            @Value("${openai.api-key:}") String apiKey,
            @Value("${openai.model:gpt-4o-mini}") String model,
            @Value("${openai.base-url:https://gms.ssafy.io/gmsapi/api.openai.com/v1/chat/completions}") String baseUrl,
            ObjectMapper objectMapper) {
        this.model = model;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
    }

    /**
     * 시스템/유저 프롬프트로 추천을 요청하고, 메뉴 선택 결과를 반환한다.
     *
     * 기대 응답(JSON): { "recommendations": [ { "menuId": 12, "reason": "..." }, ... ] }
     */
    public List<MenuPick> recommend(String systemPrompt, String userPrompt) {
        Map<String, Object> body = Map.of(
                "model", model,
                "temperature", TEMPERATURE,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                )
        );

        try {
            JsonNode root = restClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            String content = root.path("choices").path(0).path("message").path("content").asText();
            if (content.isBlank()) {
                throw new IllegalStateException("empty content from OpenAI");
            }

            JsonNode parsed = objectMapper.readTree(content);
            JsonNode items = parsed.path("recommendations");

            List<MenuPick> picks = new ArrayList<>();
            for (JsonNode node : items) {
                long menuId = node.path("menuId").asLong(0);
                String reason = node.path("reason").asText("");
                if (menuId > 0) {
                    picks.add(new MenuPick(menuId, reason));
                }
            }
            return picks;
        } catch (Exception e) {
            log.warn("OpenAI recommendation call failed: {}", e.getMessage());
            throw new BaseException(BaseResponseStatus.RECOMMENDATION_FAILED);
        }
    }

    /** LLM이 고른 메뉴 한 건 (메뉴 PK + 추천 이유). */
    public record MenuPick(Long menuId, String reason) {
    }
}
