package com.cafeminsu.global.exception;

import com.cafeminsu.global.common.BaseResponseStatus;
import lombok.Getter;

/**
 * 도메인에서 사용하는 비즈니스 예외.
 *
 * 사용 예:
 *   throw new BaseException(BaseResponseStatus.USER_NOT_FOUND);
 *
 * GlobalExceptionHandler가 가로채서 BaseResponse 형태로 응답합니다.
 */
@Getter
public class BaseException extends RuntimeException {

    private final BaseResponseStatus status;

    public BaseException(BaseResponseStatus status) {
        super(status.getMessage());
        this.status = status;
    }

    public BaseException(BaseResponseStatus status, String detail) {
        super(detail);
        this.status = status;
    }
}
