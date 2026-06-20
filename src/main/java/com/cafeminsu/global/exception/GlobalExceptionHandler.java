package com.cafeminsu.global.exception;

import com.cafeminsu.global.common.BaseResponse;
import com.cafeminsu.global.common.BaseResponseStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.stream.Collectors;

/**
 * 모든 컨트롤러에서 발생한 예외를 가로채서 BaseResponse로 변환.
 *
 * 핸들러 우선순위는 Spring이 가장 구체적인 예외부터 매칭합니다.
 * 새 예외 패턴이 생기면 위쪽에 더 구체적인 핸들러를 추가하세요.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 비즈니스 예외 */
    @ExceptionHandler(BaseException.class)
    public ResponseEntity<BaseResponse<Void>> handleBaseException(BaseException e) {
        log.warn("[BaseException] code={} message={}", e.getStatus().getCode(), e.getMessage());
        return ResponseEntity
                .status(e.getStatus().getHttpStatus())
                .body(BaseResponse.failure(e.getStatus()));
    }

    /** @Valid 검증 실패 (Request DTO) */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<BaseResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> "%s: %s".formatted(fe.getField(), fe.getDefaultMessage()))
                .collect(Collectors.joining(", "));
        log.warn("[Validation] {}", detail);
        return ResponseEntity
                .status(BaseResponseStatus.VALIDATION_FAILED.getHttpStatus())
                .body(BaseResponse.failure(BaseResponseStatus.VALIDATION_FAILED.getCode(), detail));
    }

    /** body 파싱 실패 (잘못된 JSON 등) */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<BaseResponse<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("[NotReadable] {}", e.getMessage());
        return ResponseEntity
                .status(BaseResponseStatus.INVALID_REQUEST.getHttpStatus())
                .body(BaseResponse.failure(BaseResponseStatus.INVALID_REQUEST));
    }

    /** 필수 query param 누락 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<BaseResponse<Void>> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("[MissingParam] {}", e.getMessage());
        return ResponseEntity
                .status(BaseResponseStatus.INVALID_REQUEST.getHttpStatus())
                .body(BaseResponse.failure(BaseResponseStatus.INVALID_REQUEST.getCode(), e.getMessage()));
    }

    /** 잘못된 HTTP 메서드 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<BaseResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity
                .status(BaseResponseStatus.METHOD_NOT_ALLOWED.getHttpStatus())
                .body(BaseResponse.failure(BaseResponseStatus.METHOD_NOT_ALLOWED));
    }

    /** 라우트 없음 (spring.mvc.throw-exception-if-no-handler-found=true 필요) */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<BaseResponse<Void>> handleNotFound(NoHandlerFoundException e) {
        return ResponseEntity
                .status(BaseResponseStatus.RESOURCE_NOT_FOUND.getHttpStatus())
                .body(BaseResponse.failure(BaseResponseStatus.RESOURCE_NOT_FOUND));
    }

    /** Spring Security AccessDeniedException (인증은 됐지만 권한 부족) */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<BaseResponse<Void>> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity
                .status(BaseResponseStatus.ACCESS_DENIED.getHttpStatus())
                .body(BaseResponse.failure(BaseResponseStatus.ACCESS_DENIED));
    }

    /** 멀티파트 파일 용량 초과 (컨테이너 레벨 안전망) */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<BaseResponse<Void>> handleMaxUpload(MaxUploadSizeExceededException e) {
        log.warn("[MaxUpload] {}", e.getMessage());
        return ResponseEntity
                .status(BaseResponseStatus.IMAGE_SIZE_EXCEEDED.getHttpStatus())
                .body(BaseResponse.failure(BaseResponseStatus.IMAGE_SIZE_EXCEEDED));
    }

    /** 멀티파트 필수 파트(file) 누락 */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<BaseResponse<Void>> handleMissingPart(MissingServletRequestPartException e) {
        log.warn("[MissingPart] {}", e.getMessage());
        return ResponseEntity
                .status(BaseResponseStatus.INVALID_REQUEST.getHttpStatus())
                .body(BaseResponse.failure(BaseResponseStatus.INVALID_REQUEST.getCode(), e.getMessage()));
    }

    /** 예상치 못한 모든 예외 — 마지막 안전망 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<BaseResponse<Void>> handleUnexpected(Exception e) {
        log.error("[Unexpected]", e);
        return ResponseEntity
                .status(BaseResponseStatus.INTERNAL_SERVER_ERROR.getHttpStatus())
                .body(BaseResponse.failure(BaseResponseStatus.INTERNAL_SERVER_ERROR));
    }
}
