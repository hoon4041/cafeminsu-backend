package com.cafeminsu.domain.user.service;

import com.cafeminsu.domain.user.dto.KakaoUserInfo;
import com.cafeminsu.global.common.BaseResponseStatus;
import com.cafeminsu.global.exception.BaseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 카카오 OAuth 사용자 정보 조회.
 *
 * 클라이언트가 카카오 SDK로 받은 access_token을 우리 서버에 보내면,
 * 이 클라이언트가 그걸로 카카오 API를 호출해서 사용자 정보를 가져옴.
 *
 * Spec: https://developers.kakao.com/docs/latest/ko/kakaologin/rest-api#req-user-info
 */
@Slf4j
@Component
public class KakaoOAuthClient {

    private static final String KAKAO_USER_INFO_URL = "https://kapi.kakao.com/v2/user/me";

    private final RestClient restClient;

    public KakaoOAuthClient() {
        this.restClient = RestClient.builder()
                .baseUrl(KAKAO_USER_INFO_URL)
                .build();
    }

    public KakaoUserInfo fetchUserInfo(String kakaoAccessToken) {
        try {
            return restClient.get()
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + kakaoAccessToken)
                    .header(HttpHeaders.CONTENT_TYPE,
                            MediaType.APPLICATION_FORM_URLENCODED_VALUE + ";charset=utf-8")
                    .retrieve()
                    .body(KakaoUserInfo.class);
        } catch (RestClientException e) {
            log.warn("Kakao API call failed: {}", e.getMessage());
            throw new BaseException(BaseResponseStatus.KAKAO_LOGIN_FAILED);
        }
    }
}
