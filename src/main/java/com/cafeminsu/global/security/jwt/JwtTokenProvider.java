package com.cafeminsu.global.security.jwt;

import com.cafeminsu.global.common.BaseResponseStatus;
import com.cafeminsu.global.exception.BaseException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 토큰 생성·검증·파싱.
 *
 * Access  - 1시간, claim: userId, role
 * Refresh - 14일, claim: userId만 (role은 access에서)
 *
 * 시크릿은 환경변수 JWT_SECRET. HS256은 최소 32바이트 키 필요.
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private final String secretRaw;
    private final long accessValiditySeconds;
    private final long refreshValiditySeconds;

    private SecretKey key;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secretRaw,
            @Value("${jwt.access-token-validity-seconds}") long accessValiditySeconds,
            @Value("${jwt.refresh-token-validity-seconds}") long refreshValiditySeconds
    ) {
        this.secretRaw = secretRaw;
        this.accessValiditySeconds = accessValiditySeconds;
        this.refreshValiditySeconds = refreshValiditySeconds;
    }

    @PostConstruct
    void init() {
        // Base64 디코딩을 시도하고, 실패하면 원본 바이트를 그대로 사용
        // (운영에선 반드시 Base64 인코딩된 32바이트+ 키 권장)
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secretRaw);
        } catch (RuntimeException ignore) {
            keyBytes = secretRaw.getBytes(StandardCharsets.UTF_8);
        }
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes for HS256. Current: " + keyBytes.length);
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    public String createAccessToken(Long userId, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessValiditySeconds * 1000);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .claim("type", "access")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public String createRefreshToken(Long userId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + refreshValiditySeconds * 1000);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("type", "refresh")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /** 토큰 파싱 — 만료/위조 시 예외 던짐 */
    public Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new BaseException(BaseResponseStatus.EXPIRED_TOKEN);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT: {}", e.getMessage());
            throw new BaseException(BaseResponseStatus.INVALID_TOKEN);
        }
    }

    public Long getUserId(String token) {
        return Long.parseLong(parse(token).getSubject());
    }

    public String getRole(String token) {
        return parse(token).get("role", String.class);
    }

    public long getRemainingMillis(String token) {
        Date expiry = parse(token).getExpiration();
        return Math.max(0, expiry.getTime() - System.currentTimeMillis());
    }
}
