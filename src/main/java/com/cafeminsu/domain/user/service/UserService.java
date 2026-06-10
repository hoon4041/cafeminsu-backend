package com.cafeminsu.domain.user.service;

import com.cafeminsu.domain.user.dto.BecomeOwnerRes;
import com.cafeminsu.domain.user.dto.KakaoLoginRes;
import com.cafeminsu.domain.user.dto.KakaoUserInfo;
import com.cafeminsu.domain.user.dto.LocationReq;
import com.cafeminsu.domain.user.dto.LocationRes;
import com.cafeminsu.domain.user.dto.NicknameChangeRes;
import com.cafeminsu.domain.user.dto.NicknameCheckRes;
import com.cafeminsu.domain.user.dto.OtherUserProfileRes;
import com.cafeminsu.domain.user.dto.RefreshRes;
import com.cafeminsu.domain.user.dto.SignupReq;
import com.cafeminsu.domain.user.dto.SignupRes;
import com.cafeminsu.domain.user.dto.UserProfileRes;
import com.cafeminsu.domain.user.entity.RefreshToken;
import com.cafeminsu.domain.user.entity.User;
import com.cafeminsu.domain.user.entity.UserRole;
import com.cafeminsu.domain.user.repository.RefreshTokenRepository;
import com.cafeminsu.domain.user.repository.UserRepository;
import com.cafeminsu.global.common.BaseResponseStatus;
import com.cafeminsu.global.exception.BaseException;
import com.cafeminsu.global.security.TokenBlacklistService;
import com.cafeminsu.global.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklistService tokenBlacklistService;
    private final KakaoOAuthClient kakaoOAuthClient;

    @Value("${jwt.refresh-token-validity-seconds}")
    private long refreshValiditySeconds;

    /* =========================================================
     * 1) 카카오 로그인
     * ========================================================= */
    @Transactional
    public KakaoLoginRes kakaoLogin(String kakaoAccessToken) {
        // 카카오 API로 사용자 정보 조회
        KakaoUserInfo info = kakaoOAuthClient.fetchUserInfo(kakaoAccessToken);
        if (info == null || info.id() == null) {
            throw new BaseException(BaseResponseStatus.KAKAO_LOGIN_FAILED);
        }

        // 기존 회원 조회 또는 신규 생성
        User user = userRepository.findByKakaoId(info.getKakaoIdAsString())
                .orElseGet(() -> userRepository.save(
                        User.createFromKakao(
                                info.getKakaoIdAsString(),
                                info.getEmail(),
                                info.getProfileImageUrl()
                        )
                ));

        boolean isNewUser = user.isSignupIncomplete();

        // JWT 발급
        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getRole().name());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());

        // RefreshToken DB에 저장 (디바이스별로 별도 row)
        refreshTokenRepository.save(RefreshToken.builder()
                .userId(user.getId())
                .token(refreshToken)
                .expiresAt(LocalDateTime.now().plusSeconds(refreshValiditySeconds))
                .build());

        return new KakaoLoginRes(accessToken, refreshToken, isNewUser, user.getNickname());
    }

    /* =========================================================
     * 2) 회원가입 (닉네임/프로필 설정)
     * ========================================================= */
    @Transactional
    public SignupRes signup(Long userId, SignupReq req) {
        User user = findUserOrThrow(userId);
        if (userRepository.existsByNickname(req.nickname())) {
            throw new BaseException(BaseResponseStatus.NICKNAME_DUPLICATED);
        }
        user.completeSignup(req.nickname(), req.profileImageUrl());
        return new SignupRes(user.getId(), user.getNickname());
    }

    /* =========================================================
     * 3) 로그아웃
     * ========================================================= */
    @Transactional
    public void logout(String accessToken, String refreshToken) {
        // Access Token은 만료까지 블랙리스트
        long remainingMs = jwtTokenProvider.getRemainingMillis(accessToken);
        tokenBlacklistService.blacklist(accessToken, remainingMs);

        // Refresh Token DB에서 삭제 (해당 디바이스만)
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenRepository.deleteByToken(refreshToken);
        }
    }

    /* =========================================================
     * 4) 토큰 재발급
     * ========================================================= */
    @Transactional(readOnly = true)
    public RefreshRes refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BaseException(BaseResponseStatus.REFRESH_TOKEN_NOT_FOUND);
        }
        // 1) 서명·만료 검증 (만료면 BaseException 던짐)
        Long userId = jwtTokenProvider.getUserId(refreshToken);

        // 2) DB에 있는지 확인 (로그아웃되지 않은 토큰인지)
        RefreshToken stored = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.INVALID_TOKEN));
        if (stored.isExpired()) {
            throw new BaseException(BaseResponseStatus.EXPIRED_TOKEN);
        }

        // 3) 사용자 정보로 새 Access Token 발급
        User user = findUserOrThrow(userId);
        String newAccessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getRole().name());
        return new RefreshRes(newAccessToken);
    }

    /* =========================================================
     * 5) 닉네임 중복 확인
     * ========================================================= */
    public NicknameCheckRes checkNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            throw new BaseException(BaseResponseStatus.INVALID_REQUEST);
        }
        boolean available = !userRepository.existsByNickname(nickname);
        return new NicknameCheckRes(available);
    }

    /* =========================================================
     * 6) 닉네임 변경
     * ========================================================= */
    @Transactional
    public NicknameChangeRes changeNickname(Long userId, String newNickname) {
        User user = findUserOrThrow(userId);
        if (newNickname.equals(user.getNickname())) {
            return new NicknameChangeRes(user.getNickname());
        }
        if (userRepository.existsByNickname(newNickname)) {
            throw new BaseException(BaseResponseStatus.NICKNAME_DUPLICATED);
        }
        user.changeNickname(newNickname);
        return new NicknameChangeRes(user.getNickname());
    }

    /* =========================================================
     * 7) 위치 저장
     * ========================================================= */
    @Transactional
    public void saveLocation(Long userId, LocationReq req) {
        User user = findUserOrThrow(userId);
        user.updateLocation(req.latitude(), req.longitude());
    }

    /* =========================================================
     * 8) 위치 조회
     * ========================================================= */
    public LocationRes getLocation(Long userId) {
        User user = findUserOrThrow(userId);
        return new LocationRes(user.getLatitude(), user.getLongitude());
    }

    /* =========================================================
     * 9) 내 프로필
     * ========================================================= */
    public UserProfileRes getMyProfile(Long userId) {
        return UserProfileRes.from(findUserOrThrow(userId));
    }

    /* =========================================================
     * 10) 타인 프로필
     * ========================================================= */
    public OtherUserProfileRes getOtherProfile(Long userId) {
        return OtherUserProfileRes.from(findUserOrThrow(userId));
    }

    /* =========================================================
     * 11) FCM 토큰 갱신
     * ========================================================= */
    @Transactional
    public void updateFcmToken(Long userId, String fcmToken) {
        User user = findUserOrThrow(userId);
        user.updateFcmToken(fcmToken);
    }

    /* =========================================================
     * 12) 점주 전환
     *
     * 응답 후 클라이언트는 즉시 /api/user/refresh를 호출하면
     * OWNER role이 박힌 새 Access Token을 받을 수 있음 (재로그인 불필요).
     * ========================================================= */
    @Transactional
    public BecomeOwnerRes becomeOwner(Long userId) {
        User user = findUserOrThrow(userId);
        user.becomeOwner();
        return new BecomeOwnerRes(UserRole.OWNER);
    }

    /* ============================
     * helpers
     * ============================ */
    private User findUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.USER_NOT_FOUND));
    }
}
