package com.cafeminsu.global.config;

import org.springframework.context.annotation.Configuration;

/**
 * Redis 자동 설정.
 *
 * Spring Boot의 RedisAutoConfiguration이 StringRedisTemplate, RedisTemplate을 자동 등록합니다.
 * 현재는 추가 커스터마이즈 불필요 — 나중에 직렬화 전략(Jackson2JsonRedisSerializer)이나
 * pub/sub 리스너 추가할 때 이 클래스에 Bean 추가하세요.
 */
@Configuration
public class RedisConfig {
}
