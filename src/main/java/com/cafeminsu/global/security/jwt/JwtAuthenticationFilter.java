package com.cafeminsu.global.security.jwt;

import com.cafeminsu.global.common.BaseResponseStatus;
import com.cafeminsu.global.exception.BaseException;
import com.cafeminsu.global.security.TokenBlacklistService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authorization 헤더의 Bearer 토큰을 검증해 SecurityContext에 인증 정보를 채움.
 *
 * 토큰이 없으면 그냥 통과시킴 — permitAll() 경로 처리를 위해서.
 * 토큰이 있는데 잘못됐으면 AuthenticationEntryPoint에서 401 처리.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklistService blacklistService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            String token = resolveToken(request);
            if (StringUtils.hasText(token)) {
                // 블랙리스트(로그아웃) 체크
                if (blacklistService.isBlacklisted(token)) {
                    throw new BaseException(BaseResponseStatus.BLACKLISTED_TOKEN);
                }
                Claims claims = jwtTokenProvider.parse(token);

                // Refresh 토큰으로 API 호출 시도 차단
                if (!"access".equals(claims.get("type", String.class))) {
                    throw new BaseException(BaseResponseStatus.INVALID_TOKEN);
                }

                Long userId = Long.parseLong(claims.getSubject());
                String role = claims.get("role", String.class);

                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
                var authentication = new UsernamePasswordAuthenticationToken(userId, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (BaseException e) {
            // SecurityContext 안 세팅하면 이후 EntryPoint가 401 응답
            SecurityContextHolder.clearContext();
            request.setAttribute("jwt.exception.status", e.getStatus());
        }
        chain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(AUTH_HEADER);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
