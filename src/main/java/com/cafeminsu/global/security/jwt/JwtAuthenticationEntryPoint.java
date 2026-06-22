package com.cafeminsu.global.security.jwt;

import com.cafeminsu.global.common.BaseResponseStatus;
import com.cafeminsu.global.common.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 인증 실패 시 401 응답 + ErrorResponse JSON 반환.
 *
 * JwtAuthenticationFilter가 토큰 예외를 request attribute에 저장해두면
 * 여기서 꺼내서 적절한 코드(만료/위조/블랙리스트)로 응답합니다.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        BaseResponseStatus status = (BaseResponseStatus) request.getAttribute("jwt.exception.status");
        if (status == null) {
            status = BaseResponseStatus.UNAUTHORIZED;
        }
        response.setStatus(status.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(ErrorResponse.of(status)));
    }
}
