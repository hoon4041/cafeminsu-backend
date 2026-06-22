package com.cafeminsu.global.common;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "0. Health", description = "서버 상태 체크")
@RestController
public class HealthController {

    /** 인프라(AWS ALB) 헬스체크 + 개발 중 서버 살아있는지 확인용 */
    @SecurityRequirements   // 이 엔드포인트는 JWT 불필요
    @Operation(summary = "헬스체크")
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "cafeminsu",
                "time", System.currentTimeMillis()
        );
    }
}
