package com.cafeminsu.domain.payment.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 포트원(아임포트) 결제 검증 클라이언트.
 *
 * MVP는 mock 구현. 운영에선 IamportClient로 교체:
 * <pre>
 *   IamportResponse&lt;com.siot.IamportRestClient.response.Payment&gt; res =
 *       iamportClient.paymentByImpUid(impUid);
 *   return new VerificationResult(res.getResponse().getStatus(), res.getResponse().getAmount().intValue());
 * </pre>
 *
 * Mock 동작:
 * - impUid가 "imp_fail"로 시작 → 실패 응답 (테스트 시 실패 시나리오 확인용)
 * - 그 외 → 우리 DB의 expectedAmount를 그대로 돌려줘서 자동 통과
 */
@Slf4j
@Component
public class PortoneClient {

    public VerificationResult verify(String impUid, int expectedAmount) {
        if (impUid != null && impUid.startsWith("imp_fail")) {
            log.warn("[Portone:Mock] verify FAIL impUid={}", impUid);
            return new VerificationResult("failed", 0);
        }
        log.info("[Portone:Mock] verify OK impUid={} amount={}", impUid, expectedAmount);
        return new VerificationResult("paid", expectedAmount);
    }

    public record VerificationResult(String status, int amount) {
        public boolean isPaid() {
            return "paid".equals(status);
        }
    }
}
