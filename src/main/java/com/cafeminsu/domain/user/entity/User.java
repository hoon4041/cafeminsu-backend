package com.cafeminsu.domain.user.entity;

import com.cafeminsu.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Getter
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 100)
    private String email;

    @Column(length = 20, unique = true)
    private String nickname;

    @Column(name = "kakao_id", length = 50, unique = true)
    private String kakaoId;

    /** 점주 ID/PW 로그인용. 카카오 가입 유저는 null. */
    @Column(name = "login_id", length = 50, unique = true)
    private String loginId;

    /** BCrypt 해시. 평문 저장 금지. 카카오 가입 유저는 null. */
    @Column(name = "password", length = 100)
    private String password;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "fcm_token", length = 255)
    private String fcmToken;

    @Builder
    private User(String email, String kakaoId, String loginId, String password,
                 String nickname, String profileImageUrl, UserRole role) {
        this.email = email;
        this.kakaoId = kakaoId;
        this.loginId = loginId;
        this.password = password;
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
        this.role = role;
    }

    /**
     * 점주 ID/PW 계정 생성. password는 반드시 BCrypt로 인코딩된 값을 넘길 것.
     * (보통 DB에 미리 심어두지만, 코드로 생성할 때 사용)
     */
    public static User createOwner(String loginId, String encodedPassword, String nickname) {
        return User.builder()
                .loginId(loginId)
                .password(encodedPassword)
                .nickname(nickname)
                .role(UserRole.OWNER)
                .build();
    }

    /** 카카오 OAuth 신규 가입 시 호출. nickname은 별도 회원가입 단계에서 채움. */
    public static User createFromKakao(String kakaoId, String email, String profileImageUrl) {
        return User.builder()
                .kakaoId(kakaoId)
                .email(email)
                .profileImageUrl(profileImageUrl)
                .role(UserRole.CUSTOMER)
                .build();
    }

    /** 회원가입 단계: 닉네임/프로필 이미지 설정 */
    public void completeSignup(String nickname, String profileImageUrl) {
        this.nickname = nickname;
        if (profileImageUrl != null) {
            this.profileImageUrl = profileImageUrl;
        }
    }

    public void changeNickname(String newNickname) {
        this.nickname = newNickname;
    }

    public void updateLocation(BigDecimal latitude, BigDecimal longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public void updateFcmToken(String fcmToken) {
        this.fcmToken = fcmToken;
    }

    public void becomeOwner() {
        this.role = UserRole.OWNER;
    }

    /** 회원가입 미완료(닉네임 미설정) 상태인지 */
    public boolean isSignupIncomplete() {
        return this.nickname == null;
    }
}
