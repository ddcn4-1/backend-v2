package org.ddcn41.ticketing_system.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {
    // 인증/권한 (1xxx)
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "사용자 인증에 실패했습니다"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다"),

    // 예약 관련 (2xxx)
    BOOKING_NOT_FOUND(HttpStatus.NOT_FOUND, "예매를 찾을 수 없습니다"),
    SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "스케줄을 찾을 수 없습니다"),
    BOOKING_ALREADY_CANCELLED(HttpStatus.BAD_REQUEST, "이미 취소된 예매입니다"),
    BOOKING_CANNOT_CANCEL(HttpStatus.BAD_REQUEST, "취소할 수 없는 예매입니다"),

    // 좌석 관련 (3xxx)
    INVALID_SEAT_MAP(HttpStatus.BAD_REQUEST, "좌석 맵 정보가 올바르지 않습니다"),
    SEAT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 좌석입니다"),
    SEAT_NOT_AVAILABLE(HttpStatus.BAD_REQUEST, "예약 불가능한 좌석입니다"),
    SEAT_ALREADY_BOOKED(HttpStatus.CONFLICT, "다른 사용자가 먼저 예약한 좌석이 있습니다"),
    INSUFFICIENT_SEATS(HttpStatus.BAD_REQUEST, "잔여 좌석 수가 부족합니다"),
    SEAT_CANCEL_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "좌석 취소 실패"),
    SEAT_LOCK_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "좌석 락 실패"),
    SEAT_LOCK_CANCEL_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "좌석 락 해제 실패"),

    // 대기열 관련 (4xxx)
    QUEUE_TOKEN_REQUIRED(HttpStatus.BAD_REQUEST, "대기열 토큰이 필요합니다"),
    QUEUE_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "유효하지 않은 대기열 토큰입니다"),
    QUEUE_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "대기열 서비스에 일시적으로 접근할 수 없습니다"),

    // 유저 관련 (5xxx)
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다"),
    USER_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 존재하는 사용자입니다"),
    USER_CREATE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "사용자 생성 실패"),
    USER_DELETE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "사용자 삭제 실패"),

    // 공연장 관련 (6xxx)
    VENUE_NOT_FOUND(HttpStatus.NOT_FOUND, "공연장을 찾을 수 없습니다"),

    // 공연 관련 (7xxx)
    PERFORMANCE_NOT_FOUND(HttpStatus.NOT_FOUND, "공연을 찾을 수 없습니다"),
    SCHEDULE_SOLD_OUT(HttpStatus.CONFLICT, "이미 매진된 스케줄입니다"),

    // 파일/S3 관련 (8xxx)
    FILE_REQUIRED(HttpStatus.BAD_REQUEST, "업로드할 파일이 없습니다"),
    FILE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "파일 업로드에 실패했습니다"),
    FILE_PROCESSING_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "파일 처리 중 오류가 발생했습니다"),

    // AWS Cognito 관련 (9xxx)
    COGNITO_USER_CREATE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Cognito 사용자 생성 실패"),
    COGNITO_USER_DELETE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Cognito 사용자 삭제 실패"),

    // 일반 오류 (9xxx)
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다"),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다"),
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다");

    ;

    private final HttpStatus status;
    private final String message;
}
