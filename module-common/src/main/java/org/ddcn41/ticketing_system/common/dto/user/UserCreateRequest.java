package org.ddcn41.ticketing_system.common.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCreateRequest {
    private String userId;
    private String username;
    private String email;
    private String name;
    private String password;
    private String phone;
    private String role;
    private String status;
}
