package com.cafeminsu.domain.payment.service;

import com.cafeminsu.global.common.BaseResponseStatus;
import com.cafeminsu.global.exception.BaseException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 카카오페이 결제 클라이언트 (신규 오픈API: open-api.kakaopay.com).
 *
 * 인증 헤더: {@code Authorization: SECRET_KEY {KAKAOPAY_SECRET_KEY}}.
 * 가맹점 CID·Secret key는 서버 환경변수로만 보관하며 절대 로그에 남기지 않는다.
 *
 * Secret key가 비어 있는 로컬/CI 환경에서는 mock으로 동작한다:
 *  - ready  : 가짜 tid + approval_url(딥링크)을 redirectUrl로 반환 → 앱/테스트 흐름 진행 가능
 *  - approve: pgToken이 "fail"로 시작하면 실패, 그 외엔 가짜 aid + 요청 금액 반환
 */
@Slf4j
@Component
public class KakaoPayClient {

    private static final String READY_PATH = "/online/v1/payment/ready";
    private static final String APPROVE_PATH = "/online/v1/payment/approve";

    private final String cid;
    private final boolean mock;
    private final String approvalUrl;
    private final String cancelUrl;
    private final String failUrl;
    private final RestClient restClient;

    public KakaoPayClient(
            @Value("${kakaopay.cid:TC0ONETIME}") String cid,
            @Value("${kakaopay.secret-key:}") String secretKey,
            @Value("${kakaopay.base-url:https://open-api.kakaopay.com}") String baseUrl,
            @Value("${kakaopay.approval-url:cafeminsu://kakaopay}") String approvalUrl,
            @Value("${kakaopay.cancel-url:cafeminsu://kakaopay}") String cancelUrl,
            @Value("${kakaopay.fail-url:cafeminsu://kakaopay}") String failUrl) {
        this.cid = cid;
        this.mock = secretKey == null || secretKey.isBlank();
        this.approvalUrl = approvalUrl;
        this.cancelUrl = cancelUrl;
        this.failUrl = failUrl;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "SECRET_KEY " + (secretKey == null ? "" : secretKey))
                .build();
        if (mock) {
            log.warn("[KakaoPay] secret-key 미설정 — mock으로 동작합니다.");
        }
    }

    /** 결제 준비. 카카오페이 tid와 모바일 리다이렉트 URL을 반환. */
    public ReadyResult ready(String partnerOrderId, String partnerUserId,
                             String itemName, int totalAmount) {
        if (mock) {
            String tid = "T_mock_" + UUID.randomUUID().toString().substring(0, 12);
            log.info("[KakaoPay:mock] ready order={} amount={} tid={}", partnerOrderId, totalAmount, tid);
            return new ReadyResult(tid, approvalUrl);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cid", cid);
        body.put("partner_order_id", partnerOrderId);
        body.put("partner_user_id", partnerUserId);
        body.put("item_name", itemName);
        body.put("quantity", 1);
        body.put("total_amount", totalAmount);
        body.put("tax_free_amount", 0);
        body.put("approval_url", approvalUrl);
        body.put("cancel_url", cancelUrl);
        body.put("fail_url", failUrl);

        try {
            JsonNode res = restClient.post()
                    .uri(READY_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            String tid = res.path("tid").asText();
            String redirect = res.path("next_redirect_mobile_url").asText();
            return new ReadyResult(tid, redirect);
        } catch (Exception e) {
            log.warn("[KakaoPay] ready 실패 order={}: {}", partnerOrderId, e.getMessage());
            throw new BaseException(BaseResponseStatus.PAYMENT_VERIFICATION_FAILED, "카카오페이 결제 준비에 실패했습니다.");
        }
    }

    /**
     * 결제 승인. 카카오페이 aid(승인번호)와 승인 금액을 반환.
     * expectedAmount는 mock 모드에서 승인 금액을 echo하는 용도이며, 실서버는 카카오페이 응답 금액을 사용한다.
     */
    public ApproveResult approve(String tid, String partnerOrderId,
                                 String partnerUserId, String pgToken, int expectedAmount) {
        if (mock) {
            if (pgToken != null && pgToken.startsWith("fail")) {
                log.warn("[KakaoPay:mock] approve FAIL order={} tid={}", partnerOrderId, tid);
                throw new BaseException(BaseResponseStatus.PAYMENT_VERIFICATION_FAILED, "카카오페이 결제 승인에 실패했습니다.");
            }
            String aid = "A_mock_" + UUID.randomUUID().toString().substring(0, 12);
            log.info("[KakaoPay:mock] approve order={} tid={} aid={}", partnerOrderId, tid, aid);
            return new ApproveResult(aid, expectedAmount);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cid", cid);
        body.put("tid", tid);
        body.put("partner_order_id", partnerOrderId);
        body.put("partner_user_id", partnerUserId);
        body.put("pg_token", pgToken);

        try {
            JsonNode res = restClient.post()
                    .uri(APPROVE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            String aid = res.path("aid").asText();
            int amount = res.path("amount").path("total").asInt();
            return new ApproveResult(aid, amount);
        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[KakaoPay] approve 실패 order={}: {}", partnerOrderId, e.getMessage());
            throw new BaseException(BaseResponseStatus.PAYMENT_VERIFICATION_FAILED, "카카오페이 결제 승인에 실패했습니다.");
        }
    }

    public boolean isMock() {
        return mock;
    }

    public record ReadyResult(String tid, String redirectUrl) {}

    public record ApproveResult(String aid, int amount) {}
}
