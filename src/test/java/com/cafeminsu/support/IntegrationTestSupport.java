package com.cafeminsu.support;

import com.cafeminsu.global.security.TokenBlacklistService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 모든 통합 테스트의 베이스.
 *
 * - @SpringBootTest: 전체 스프링 컨텍스트 로드
 * - @Transactional: 각 테스트 메서드 종료 시 rollback → 테스트 간 격리
 * - Redis 자동설정 제외 + TokenBlacklistService mock → Redis 없이 실행 가능
 * - test 프로파일 활성화 → H2 in-memory DB 사용
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
})
public abstract class IntegrationTestSupport {

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected TestFixtures fixtures;

    /** TokenBlacklistService는 Redis 의존이라 mock으로 대체. isBlacklisted는 기본 false 반환. */
    @MockBean protected TokenBlacklistService tokenBlacklistService;
}
