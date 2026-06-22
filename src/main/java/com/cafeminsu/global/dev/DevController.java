package com.cafeminsu.global.dev;

import com.cafeminsu.domain.user.entity.RefreshToken;
import com.cafeminsu.domain.user.entity.User;
import com.cafeminsu.domain.user.entity.UserRole;
import com.cafeminsu.domain.user.repository.RefreshTokenRepository;
import com.cafeminsu.domain.user.repository.UserRepository;
import com.cafeminsu.global.security.jwt.JwtTokenProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * [로컬 전용] 카카오 OAuth 없이 빠르게 JWT 발급받는 개발 도구.
 *
 * @Profile("local")이 박혀 있어서 local 프로파일이 아니면 빈 자체가 등록되지 않음.
 * 운영(dev, prod 등)에 배포해도 컨트롤러가 존재하지 않아 호출 자체가 404.
 */
@Slf4j
@Profile("local")
@Tag(name = "9. Dev", description = "[로컬 전용] 개발 편의 도구. 운영 배포 시 자동 비활성화.")
@RestController
@RequestMapping("/api/dev")
@RequiredArgsConstructor
public class DevController {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${jwt.refresh-token-validity-seconds}")
    private long refreshValiditySeconds;

    @SecurityRequirements
    @Operation(summary = "[Dev] 빠른 로그인",
            description = """
                    카카오 OAuth 없이 바로 JWT를 발급받는 개발용 엔드포인트.
                    
                    동작:
                    1. nickname으로 user를 찾고 없으면 생성 (idempotent — 같은 nickname 두 번 호출 = 같은 user)
                    2. role을 OWNER로 요청하면 user의 role을 OWNER로 즉시 전환
                    3. Access + Refresh Token 발급 후 응답에 포함
                    
                    사용 흐름:
                    1. 이 API 호출 → accessToken 복사
                    2. 우측 상단 Authorize 버튼 클릭 → 토큰 붙여넣기
                    3. 모든 API 호출 시 자동으로 인증 헤더 첨부됨
                    """)
    @PostMapping("/login")
    @Transactional
    public DevLoginRes login(@Valid @RequestBody DevLoginReq req) {
        UserRole role = req.role() != null ? req.role() : UserRole.CUSTOMER;
        // 가짜 kakao_id로 user 식별. 같은 nickname → 같은 fakeKakaoId → 같은 user
        String fakeKakaoId = "dev-" + req.nickname();

        User user = userRepository.findByKakaoId(fakeKakaoId).orElse(null);
        if (user == null) {
            user = User.createFromKakao(fakeKakaoId, fakeKakaoId + "@dev.local", null);
            user.completeSignup(req.nickname(), null);
            user = userRepository.save(user);
            log.info("[Dev] 새 user 생성: id={} nickname={} role={}",
                    user.getId(), user.getNickname(), user.getRole());
        }

        // OWNER로 요청 시 즉시 전환 (CUSTOMER로 다운그레이드는 지원 안 함)
        if (role == UserRole.OWNER && user.getRole() != UserRole.OWNER) {
            user.becomeOwner();
            log.info("[Dev] user {} → OWNER 전환", user.getId());
        }

        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getRole().name());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());

        refreshTokenRepository.save(RefreshToken.builder()
                .userId(user.getId())
                .token(refreshToken)
                .expiresAt(LocalDateTime.now().plusSeconds(refreshValiditySeconds))
                .build());

        return new DevLoginRes(
                user.getId(),
                user.getNickname(),
                user.getRole(),
                accessToken,
                refreshToken
        );
    }

    public record DevLoginReq(
            @NotBlank(message = "nickname은 필수입니다.")
            @Size(min = 1, max = 20)
            String nickname,

            UserRole role     // null이면 CUSTOMER
    ) {
    }

    public record DevLoginRes(
            Long userId,
            String nickname,
            UserRole role,
            String accessToken,
            String refreshToken
    ) {
    }
}
