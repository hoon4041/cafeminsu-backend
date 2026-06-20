package com.cafeminsu.global.config;

import com.cafeminsu.global.security.LoginUserIdArgumentResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final LoginUserIdArgumentResolver loginUserIdArgumentResolver;

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(loginUserIdArgumentResolver);
    }

    /**
     * 업로드된 메뉴 이미지를 외부 디렉토리(${file.upload-dir}/menu)에서 서빙.
     * 번들 기본 svg는 기존 classpath 정적 서빙(/imgs/menu/*.svg) 그대로 유지된다.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path menuDir = Paths.get(uploadDir, "menu").toAbsolutePath().normalize();
        String location = menuDir.toUri().toString();
        if (!location.endsWith("/")) {
            location += "/";
        }
        registry.addResourceHandler("/imgs/menu/uploads/**")
                .addResourceLocations(location);
    }
}
