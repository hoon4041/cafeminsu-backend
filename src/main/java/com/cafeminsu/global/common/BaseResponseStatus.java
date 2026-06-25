package com.cafeminsu.global.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 응답 코드 표준.
 *
 * 코드 영역 (도메인별 4자리):
 *   1000        성공
 *   2000~2099   공통/시스템 에러
 *   2100~2199   인증/인가
 *   2200~2299   User 도메인
 *   2300~2399   Store 도메인
 *   2400~2499   Menu 도메인
 *   2500~2599   Order 도메인
 *   2600~2699   Payment 도메인
 *   2700~2799   Gifticon 도메인
 *   2800~2899   Stamp 도메인
 *   2900~2999   Notification 도메인
 *   3000~3099   Recommendation 도메인
 *   3100~3199   NFC 도메인
 *
 * 안드로이드 팀은 code 값으로 분기 가능. 새 에러 추가 시 enum에 한 줄씩만 추가하세요.
 */
@Getter
public enum BaseResponseStatus {

    // ===== 성공 =====
    SUCCESS(HttpStatus.OK, 1000, "요청에 성공했습니다."),

    // ===== 공통/시스템 (2000~2099) =====
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, 2000, "서버 오류가 발생했습니다."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, 2001, "요청 형식이 올바르지 않습니다."),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, 2002, "요청 값 검증에 실패했습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, 2003, "허용되지 않은 HTTP 메서드입니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, 2004, "요청한 리소스를 찾을 수 없습니다."),
    EMPTY_FILE(HttpStatus.BAD_REQUEST, 2005, "업로드할 파일이 비어 있습니다."),
    UNSUPPORTED_IMAGE_TYPE(HttpStatus.BAD_REQUEST, 2006, "지원하지 않는 이미지 형식입니다. (jpg, jpeg, png, webp만 허용)"),
    IMAGE_SIZE_EXCEEDED(HttpStatus.PAYLOAD_TOO_LARGE, 2007, "이미지 용량이 허용치를 초과했습니다."),
    FILE_STORAGE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, 2008, "파일 저장에 실패했습니다."),

    // ===== 인증/인가 (2100~2199) =====
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, 2100, "인증이 필요합니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, 2101, "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, 2102, "만료된 토큰입니다."),
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, 2104, "리프레시 토큰이 없습니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, 2105, "권한이 없습니다."),

    // ===== User (2200~2299) =====
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, 2200, "존재하지 않는 사용자입니다."),
    NICKNAME_DUPLICATED(HttpStatus.CONFLICT, 2201, "이미 사용 중인 닉네임입니다."),
    KAKAO_LOGIN_FAILED(HttpStatus.UNAUTHORIZED, 2202, "카카오 로그인에 실패했습니다."),
    NOT_AN_OWNER(HttpStatus.FORBIDDEN, 2203, "점주 권한이 없습니다."),
    OWNER_LOGIN_FAILED(HttpStatus.UNAUTHORIZED, 2204, "아이디 또는 비밀번호가 올바르지 않습니다."),

    // ===== Store (2300~2399) =====
    STORE_NOT_FOUND(HttpStatus.NOT_FOUND, 2300, "존재하지 않는 매장입니다."),
    NOT_STORE_OWNER(HttpStatus.FORBIDDEN, 2301, "본인 매장이 아닙니다."),

    // ===== Menu (2400~2499) =====
    MENU_NOT_FOUND(HttpStatus.NOT_FOUND, 2400, "존재하지 않는 메뉴입니다."),
    MENU_NOT_AVAILABLE(HttpStatus.BAD_REQUEST, 2401, "현재 판매 중지된 메뉴입니다."),
    MENU_OPTION_NOT_FOUND(HttpStatus.NOT_FOUND, 2402, "존재하지 않는 메뉴 옵션입니다."),

    // ===== Order (2500~2599) =====
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, 2500, "존재하지 않는 주문입니다."),
    INVALID_ORDER_STATUS(HttpStatus.BAD_REQUEST, 2501, "주문 상태가 올바르지 않습니다."),
    ORDER_CANNOT_BE_CANCELLED(HttpStatus.BAD_REQUEST, 2502, "취소할 수 없는 주문입니다."),

    // ===== Payment (2600~2699) =====
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, 2600, "존재하지 않는 결제입니다."),
    PAYMENT_AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, 2601, "결제 금액이 일치하지 않습니다."),
    PAYMENT_VERIFICATION_FAILED(HttpStatus.BAD_REQUEST, 2602, "결제 검증에 실패했습니다."),
    SPLIT_PAYMENT_AMOUNT_INVALID(HttpStatus.BAD_REQUEST, 2603, "분할결제 금액 합계가 주문 금액과 다릅니다."),

    // ===== Gifticon (2700~2799) =====
    GIFTICON_NOT_FOUND(HttpStatus.NOT_FOUND, 2700, "존재하지 않는 기프티콘입니다."),
    GIFTICON_ALREADY_USED(HttpStatus.BAD_REQUEST, 2701, "이미 모두 사용된 기프티콘입니다."),
    GIFTICON_EXPIRED(HttpStatus.BAD_REQUEST, 2702, "만료된 기프티콘입니다."),
    GIFTICON_INSUFFICIENT_BALANCE(HttpStatus.BAD_REQUEST, 2703, "기프티콘 잔액이 부족합니다."),
    GIFTICON_INVALID_CODE(HttpStatus.BAD_REQUEST, 2704, "유효하지 않은 클레임 코드입니다."),
    GIFTICON_ALREADY_CLAIMED(HttpStatus.CONFLICT, 2705, "이미 등록된 기프티콘입니다."),

    // ===== Stamp (2800~2899) =====
    STAMP_NOT_FOUND(HttpStatus.NOT_FOUND, 2800, "스탬프 정보가 없습니다."),

    // ===== Notification (2900~2999) =====
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, 2900, "존재하지 않는 알림입니다."),
    FCM_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, 2901, "FCM 발송에 실패했습니다."),

    // ===== Recommendation (3000~3099) =====
    RECOMMENDATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, 3000, "메뉴 추천 생성에 실패했습니다."),
    NO_MENU_TO_RECOMMEND(HttpStatus.BAD_REQUEST, 3001, "추천할 메뉴가 없습니다."),

    // ===== NFC (3100~3199) =====
    NFC_TAG_NOT_FOUND(HttpStatus.NOT_FOUND, 3100, "존재하지 않는 NFC 태그입니다."),
    NFC_TAG_INACTIVE(HttpStatus.BAD_REQUEST, 3101, "비활성화된 NFC 태그입니다."),
    NFC_CLAIM_COOLDOWN(HttpStatus.CONFLICT, 3102, "오늘은 이미 이 태그로 쿠폰을 받았습니다.");

    private final HttpStatus httpStatus;
    private final int code;
    private final String message;

    BaseResponseStatus(HttpStatus httpStatus, int code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }
}
