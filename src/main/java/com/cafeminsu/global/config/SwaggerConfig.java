package com.cafeminsu.global.config;

import com.cafeminsu.global.security.LoginUserId;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    private static final String BEARER_AUTH = "bearerAuth";

    // @LoginUserId는 토큰에서 주입되는 값이므로 Swagger 파라미터 문서에서 제외한다.
    // (이 설정이 없으면 springdoc이 query 파라미터로 잘못 노출함)
    static {
        SpringDocUtils.getConfig().addAnnotationsToIgnore(LoginUserId.class);
    }

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("카페민수 API")
                        .description("O2O 카페 플랫폼 — 스탬프 · 기프티콘")
                        .version("v0.0.1"))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Authorization: Bearer <JWT>")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }
}
