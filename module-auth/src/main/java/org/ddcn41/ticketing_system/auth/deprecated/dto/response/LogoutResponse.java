package org.ddcn41.ticketing_system.auth.deprecated.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogoutResponse {
    private String message;
    private String username;
    private String tokenTimeLeft;

    @JsonInclude(JsonInclude.Include.NON_NULL)  // null인 경우 JSON에 포함하지 않음
    private String cognitoLogoutUrl;
    // username만 받는 생성자
    public LogoutResponse(String username) {
        this.username = username;
    }

    // username과 tokenTimeLeft
    public LogoutResponse(String username, String tokenTimeLeft) {
        this.username = username;
        this.tokenTimeLeft = tokenTimeLeft;
    }

    /**
     * JWT 로그아웃용 - username과 남은 시간
     */
    public static LogoutResponse forJwtLogout(String username, String timeLeft) {
        return LogoutResponse.builder()
                .username(username)
                .tokenTimeLeft(timeLeft)
                .build();
    }

    /**
     * Cognito 로그아웃용 - message와 로그아웃 URL
     */
    public static LogoutResponse forCognitoLogout(String message, String cognitoLogoutUrl) {
        return LogoutResponse.builder()
                .message(message)
                .cognitoLogoutUrl(cognitoLogoutUrl)
                .build();
    }

    /**
     * 단순 메시지용
     */
    public static LogoutResponse withMessage(String message) {
        return LogoutResponse.builder()
                .message(message)
                .build();
    }

    /**
     * Cognito 로그아웃 완료용 (username 포함)
     */
    public static LogoutResponse forCognitoUser(String username, String message) {
        return LogoutResponse.builder()
                .username(username)
                .message(message)
                .build();
    }

}