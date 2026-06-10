package com.cafeminsu.global.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러에서 인증된 사용자 ID 주입.
 *
 * 사용 예:
 *   @GetMapping("/api/user/profile")
 *   public BaseResponse&lt;UserProfileRes&gt; getProfile(@LoginUserId Long userId) { ... }
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface LoginUserId {
}
