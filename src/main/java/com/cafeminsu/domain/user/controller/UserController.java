package com.cafeminsu.domain.user.controller;

import com.cafeminsu.domain.user.dto.BecomeOwnerRes;
import com.cafeminsu.domain.user.dto.FcmTokenReq;
import com.cafeminsu.domain.user.dto.KakaoLoginReq;
import com.cafeminsu.domain.user.dto.KakaoLoginRes;
import com.cafeminsu.domain.user.dto.LocationReq;
import com.cafeminsu.domain.user.dto.LocationRes;
import com.cafeminsu.domain.user.dto.NicknameChangeReq;
import com.cafeminsu.domain.user.dto.NicknameChangeRes;
import com.cafeminsu.domain.user.dto.NicknameCheckRes;
import com.cafeminsu.domain.user.dto.OtherUserProfileRes;
import com.cafeminsu.domain.user.dto.OwnerLoginReq;
import com.cafeminsu.domain.user.dto.OwnerLoginRes;
import com.cafeminsu.domain.user.dto.RefreshRes;
import com.cafeminsu.domain.user.dto.SignupReq;
import com.cafeminsu.domain.user.dto.SignupRes;
import com.cafeminsu.domain.user.dto.UserProfileRes;
import com.cafeminsu.domain.user.service.UserService;
import com.cafeminsu.global.common.BaseResponse;
import com.cafeminsu.global.security.LoginUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "1. User", description = "회원·인증 API")
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /* 1. 카카오 로그인 */
    @SecurityRequirements
    @Operation(summary = "카카오 로그인",
            description = "카카오 SDK로 받은 access_token으로 우리 서버 JWT 발급. " +
                    "isNewUser=true면 회원가입 화면으로 이동.")
    @PostMapping("/kakao-login")
    public BaseResponse<KakaoLoginRes> kakaoLogin(@Valid @RequestBody KakaoLoginReq req) {
        return BaseResponse.success(userService.kakaoLogin(req.accessToken()));
    }

    /* 1-2. 점주 로그인 (ID/PW) */
    @SecurityRequirements
    @Operation(summary = "점주 로그인",
            description = "사전 등록된 점주 계정의 loginId/password로 JWT 발급. " +
                    "카카오 로그인과 동일한 형태의 토큰을 반환하므로 이후 OWNER API에 그대로 사용.")
    @PostMapping("/owner-login")
    public BaseResponse<OwnerLoginRes> ownerLogin(@Valid @RequestBody OwnerLoginReq req) {
        return BaseResponse.success(userService.ownerLogin(req.loginId(), req.password()));
    }

    /* 2. 회원가입 (닉네임/프로필 설정) */
    @Operation(summary = "회원가입",
            description = "최초 카카오 로그인 후 nickname이 null인 경우 호출. 닉네임/프로필 이미지 설정.")
    @PostMapping("/signup")
    public BaseResponse<SignupRes> signup(@LoginUserId Long userId,
                                          @Valid @RequestBody SignupReq req) {
        return BaseResponse.success(userService.signup(userId, req));
    }

    /* 3. 로그아웃 */
    @Operation(summary = "로그아웃",
            description = "Refresh Token을 DB에서 삭제. Access Token은 클라이언트가 폐기하며 만료 시 자동 무효화.")
    @PostMapping("/logout")
    public BaseResponse<Void> logout(@RequestHeader(value = "Refresh-Token", required = false) String refreshToken) {
        userService.logout(refreshToken);
        return BaseResponse.success();
    }

    /* 4. 토큰 재발급 */
    @SecurityRequirements
    @Operation(summary = "토큰 재발급",
            description = "Refresh-Token 헤더로 새 Access Token 발급. Refresh Token은 그대로 유지.")
    @PostMapping("/refresh")
    public BaseResponse<RefreshRes> refresh(@RequestHeader("Refresh-Token") String refreshToken) {
        return BaseResponse.success(userService.refresh(refreshToken));
    }

    /* 5. 닉네임 중복 확인 */
    @SecurityRequirements
    @Operation(summary = "닉네임 중복 확인", description = "회원가입/변경 전 호출.")
    @GetMapping("/nickname/check")
    public BaseResponse<NicknameCheckRes> checkNickname(@RequestParam String nickname) {
        return BaseResponse.success(userService.checkNickname(nickname));
    }

    /* 6. 닉네임 변경 */
    @Operation(summary = "닉네임 변경")
    @PatchMapping("/nickname")
    public BaseResponse<NicknameChangeRes> changeNickname(@LoginUserId Long userId,
                                                         @Valid @RequestBody NicknameChangeReq req) {
        return BaseResponse.success(userService.changeNickname(userId, req.nickname()));
    }

    /* 7. 위치 저장 */
    @Operation(summary = "위치 저장", description = "주변 매장 검색용. 앱 실행 시 위치 권한 허용 후 호출.")
    @PostMapping("/location")
    public BaseResponse<Void> saveLocation(@LoginUserId Long userId,
                                           @Valid @RequestBody LocationReq req) {
        userService.saveLocation(userId, req);
        return BaseResponse.success();
    }

    /* 8. 위치 조회 */
    @Operation(summary = "위치 조회")
    @GetMapping("/location")
    public BaseResponse<LocationRes> getLocation(@LoginUserId Long userId) {
        return BaseResponse.success(userService.getLocation(userId));
    }

    /* 9. 내 프로필 */
    @Operation(summary = "내 프로필 조회")
    @GetMapping("/profile")
    public BaseResponse<UserProfileRes> getMyProfile(@LoginUserId Long userId) {
        return BaseResponse.success(userService.getMyProfile(userId));
    }

    /* 10. 타인 프로필 */
    @Operation(summary = "타인 프로필 조회",
            description = "기프티콘 선물 시 수신자 조회 등에 사용.")
    @GetMapping("/profile/{userId}")
    public BaseResponse<OtherUserProfileRes> getOtherProfile(@PathVariable Long userId) {
        return BaseResponse.success(userService.getOtherProfile(userId));
    }

    /* 11. FCM 토큰 갱신 */
    @Operation(summary = "FCM 토큰 등록/갱신",
            description = "앱 실행마다, 또는 토큰 변경 감지 시 호출.")
    @PostMapping("/fcm-token")
    public BaseResponse<Void> updateFcmToken(@LoginUserId Long userId,
                                             @Valid @RequestBody FcmTokenReq req) {
        userService.updateFcmToken(userId, req.fcmToken());
        return BaseResponse.success();
    }

    /* 12. 점주 전환 */
    @Operation(summary = "점주 전환",
            description = "role을 CUSTOMER → OWNER. 응답 후 /api/user/refresh를 호출하면 " +
                    "OWNER role이 박힌 새 Access Token을 받을 수 있습니다.")
    @PostMapping("/become-owner")
    public BaseResponse<BecomeOwnerRes> becomeOwner(@LoginUserId Long userId) {
        return BaseResponse.success(userService.becomeOwner(userId));
    }
}
