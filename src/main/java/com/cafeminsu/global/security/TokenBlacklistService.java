package com.cafeminsu.global.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 로그아웃된 Access Token을 Redis에 저장하는 블랙리스트.
 *
 * 키 형식: blacklist:{token}
 * TTL    : 토큰의 남은 만료 시간 (만료되면 자동 삭제 → 메모리 누수 없음)
 */
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String KEY_PREFIX = "blacklist:";

    private final StringRedisTemplate redisTemplate;

    public void blacklist(String token, long remainingMillis) {
        if (remainingMillis <= 0) return;
        redisTemplate.opsForValue().set(
                KEY_PREFIX + token,
                "1",
                Duration.ofMillis(remainingMillis)
        );
    }

    public boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + token));
    }
}
