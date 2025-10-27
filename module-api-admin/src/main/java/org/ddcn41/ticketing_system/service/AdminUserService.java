package org.ddcn41.ticketing_system.service;

import lombok.RequiredArgsConstructor;
import org.ddcn41.ticketing_system.common.client.UserClient;
import org.ddcn41.ticketing_system.common.dto.user.UserCreateRequest;
import org.ddcn41.ticketing_system.common.dto.user.UserResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminUserService {
    private final UserClient userClient;

    public List<UserResponse> getAllUsers() {
        return userClient.getAllUsers();
    }

    public List<UserResponse> searchUsers(String username, String role, String status) {
        return userClient.searchUsers(username, role, status);
    }

    public UserResponse getUserById(String userId) {
        return userClient.getUserById(userId);
    }

    public UserResponse createUser(UserCreateRequest request) {
        return userClient.createUser(request);
    }

    public void deleteUser(String userId) {
        userClient.deleteUser(userId);
    }
}
