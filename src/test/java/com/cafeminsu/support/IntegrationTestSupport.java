package com.cafeminsu.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 모든 통합 테스트의 베이스.
 *
 * - @SpringBootTest: 전체 스프링 컨텍스트 로드
 * - @Transactional: 각 테스트 메서드 종료 시 rollback → 테스트 간 격리
 * - test 프로파일 활성화 → H2 in-memory DB 사용
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class IntegrationTestSupport {

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected TestFixtures fixtures;
}
