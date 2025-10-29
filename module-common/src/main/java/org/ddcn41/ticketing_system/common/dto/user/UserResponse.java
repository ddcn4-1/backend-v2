package org.ddcn41.ticketing_system.common.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private String userId;
    private String username;
    private String email;
    private String name;
    private String phone;
    private String role;
    private String status;
    //TODO: role, status 검증 로직 추가
}
