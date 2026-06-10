package com.cafeminsu.global.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * 공통 응답 래퍼.
 *
 * API 명세서 포맷 그대로:
 *   { "isSuccess": true, "code": 1000, "result": { ... } }
 *
 * 실패 응답:
 *   { "isSuccess": false, "code": 2000, "message": "..." }
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"isSuccess", "code", "message", "result"})
public record BaseResponse<T>(
        boolean isSuccess,
        int code,
        String message,
        T result
) {

    /** 성공 (result 포함) */
    public static <T> BaseResponse<T> success(T result) {
        return new BaseResponse<>(true, BaseResponseStatus.SUCCESS.getCode(), null, result);
    }

    /** 성공 (result 없음) */
    public static BaseResponse<Void> success() {
        return new BaseResponse<>(true, BaseResponseStatus.SUCCESS.getCode(), null, null);
    }

    /** 실패 (BaseResponseStatus 기반) */
    public static BaseResponse<Void> failure(BaseResponseStatus status) {
        return new BaseResponse<>(false, status.getCode(), status.getMessage(), null);
    }

    /** 실패 (직접 메시지) */
    public static BaseResponse<Void> failure(int code, String message) {
        return new BaseResponse<>(false, code, message, null);
    }
}
