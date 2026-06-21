package com.cafeminsu.global.config;

import com.cafeminsu.global.security.jwt.JwtAuthenticationEntryPoint;
import com.cafeminsu.global.security.jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableMethodSecurity   // 컨트롤러에서 @PreAuthorize 사용 가능
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    /**
     * 인증 없이 접근 가능한 경로.
     * 새 public 엔드포인트 추가 시 여기에 등록하세요.
     *
     * 주의: 매장 관련 GET은 와일드카드(/api/stores/*)로 한 번에 열면
     * /api/stores/my 같은 인증 필요 경로까지 같이 열려버립니다.
     * 그래서 매장 관련은 PUBLIC_ENDPOINTS에 두지 않고 authorizeHttpRequests에서
     * GET 메서드 단위로 명시적으로 처리합니다.
     */
    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/user/kakao-login",
            "/api/user/owner-login",
            "/api/user/refresh",
            "/api/user/nickname/check",
            // [로컬 전용] DevController는 @Profile("local")로 운영에선 빈 자체가 없음.
            // 그래서 운영에 배포되어도 이 경로는 자동으로 404.
            "/api/dev/**",
            // Swagger
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            // health
            "/health",
            "/actuator/**",
            // 정적 리소스 (메뉴 이미지 등) — 비로그인 접근 허용
            "/imgs/**"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(eh -> eh.authenticationEntryPoint(jwtAuthenticationEntryPoint))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()

                        /* ===== Store =====
                         * 등록 순서 중요: 더 구체적인 규칙을 위에. /my는 /{storeId}보다 먼저.
                         */
                        .requestMatchers(HttpMethod.GET, "/api/stores/my").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/stores").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/stores/nearby").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/stores/*").permitAll()  // 상세
                        .requestMatchers(HttpMethod.POST, "/api/stores").hasRole("OWNER")
                        .requestMatchers(HttpMethod.PATCH, "/api/stores/**").hasRole("OWNER")
                        .requestMatchers(HttpMethod.DELETE, "/api/stores/**").hasRole("OWNER")

                        /* ===== Menu =====
                         * GET 두 종은 비로그인 OK (메뉴 둘러보기).
                         * 나머지(POST/PATCH/DELETE 모두)는 OWNER 권한 필요.
                         * 단, 서비스 레이어에서 menu→store→owner 체인으로 본인 매장만 손댈 수 있게 한 번 더 검증. */
                        .requestMatchers(HttpMethod.GET, "/api/stores/*/menus").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/menus/*").permitAll()
                        .requestMatchers("/api/stores/*/menus/**").hasRole("OWNER")
                        .requestMatchers("/api/menus/**").hasRole("OWNER")
                        .requestMatchers("/api/menu-options/**").hasRole("OWNER")

                        /* ===== Order (점주 전용 상태 전이) ===== */
                        .requestMatchers("/api/orders/*/accept").hasRole("OWNER")
                        .requestMatchers("/api/orders/*/ready").hasRole("OWNER")
                        .requestMatchers("/api/orders/*/complete").hasRole("OWNER")
                        .requestMatchers("/api/stores/*/orders").hasRole("OWNER")
                        .requestMatchers("/api/stores/*/payments").hasRole("OWNER")

                        /* ===== Image 업로드 (점주 전용) ===== */
                        .requestMatchers(HttpMethod.POST, "/api/images/**").hasRole("OWNER")

                        // 나머지는 로그인 필요
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // 안드로이드 앱은 CORS 영향 없지만, 웹 어드민·Swagger·로컬 개발 편의를 위해
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization", "Refresh-Token"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
