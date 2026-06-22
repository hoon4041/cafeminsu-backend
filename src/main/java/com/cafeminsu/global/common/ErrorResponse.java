package com.cafeminsu.global.common;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * 실패 응답 포맷.
 *
 * 성공은 DTO를 그대로(raw) 반환하고 HTTP 200을 사용합니다.
 * 실패는 적절한 4xx/5xx HTTP status와 함께 이 포맷으로 응답합니다:
 *   { "code": "USER_NOT_FOUND", "message": "존재하지 않는 사용자입니다." }
 *
 * code 는 BaseResponseStatus enum 이름(자기설명적 문자열)입니다.
 * 안드로이드 팀은 HTTP status로 큰 분류를, code 문자열로 세부 분기를 합니다.
 */
@JsonPropertyOrder({"code", "message"})
public record ErrorResponse(
        String code,
        String message
) {

    public static ErrorResponse of(BaseResponseStatus status) {
        return new ErrorResponse(status.name(), status.getMessage());
    }

    /** 검증 실패처럼 동적 메시지가 필요한 경우 */
    public static ErrorResponse of(BaseResponseStatus status, String message) {
        return new ErrorResponse(status.name(), message);
    }
}
