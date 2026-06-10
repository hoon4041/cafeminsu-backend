package com.cafeminsu.domain.order.service;

import com.cafeminsu.domain.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * 매장별로 충돌하지 않는 4자리 영숫자 주문번호 생성.
 *
 * 사용 문자: 0/O, 1/I 제외 — 매장에서 직원이 손님에게 외칠 때 헷갈리지 않게.
 * 풀: 32자 × 4자리 = 1,048,576 가지.
 *
 * 충돌 시 최대 10회 재시도. 그래도 안 되면(거의 불가능) 예외.
 */
@Component
@RequiredArgsConstructor
public class OrderNumberGenerator {

    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int LEN = 4;
    private static final int MAX_RETRY = 10;

    private final SecureRandom random = new SecureRandom();
    private final OrderRepository orderRepository;

    public String generate(Long storeId) {
        for (int i = 0; i < MAX_RETRY; i++) {
            String candidate = randomCode();
            if (!orderRepository.existsByStoreIdAndOrderNumber(storeId, candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("주문번호 생성 실패 (재시도 " + MAX_RETRY + "회 초과)");
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(LEN);
        for (int i = 0; i < LEN; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
