package org.ddcn41.common.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 모든 API 응답에 사용되는 공통 응답 형식
 *
 * @param <T> 응답 데이터 타입
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    /**
     * 요청 성공 여부
     */
    private boolean success;

    /**
     * 응답 데이터
     */
    private T data;

    /**
     * 메시지 (에러 메시지 또는 성공 메시지)
     */
    private String message;

    /**
     * 응답 시간
     */
    private LocalDateTime timestamp;

    /**
     * 성공 응답 생성 (데이터만)
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, LocalDateTime.now());
    }

    /**
     * 성공 응답 생성 (데이터 + 메시지)
     */
    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, data, message, LocalDateTime.now());
    }

    /**
     * 실패 응답 생성
     */
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, null, message, LocalDateTime.now());
    }

    /**
     * 실패 응답 생성 (데이터 포함)
     */
    public static <T> ApiResponse<T> error(T data, String message) {
        return new ApiResponse<>(false, data, message, LocalDateTime.now());
    }
}