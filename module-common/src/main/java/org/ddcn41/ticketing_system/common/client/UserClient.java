package org.ddcn41.ticketing_system.common.client;

import org.ddcn41.ticketing_system.common.dto.user.UserCreateRequest;
import org.ddcn41.ticketing_system.common.dto.user.UserResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "user-service", url = "${user.service.url:http://localhost:8082}")
public interface UserClient {
    @GetMapping("/v1/internal/users")
    List<UserResponse> getAllUsers();

    @GetMapping("/v1/internal/users/{userId}")
    UserResponse getUserById(@PathVariable("userId") String userId);

    @GetMapping("/v1/internal/users")
    List<UserResponse> searchUsers(@RequestParam(value = "username", required = false) String username,
                                   @RequestParam(value = "role", required = false) String role,
                                   @RequestParam(value = "status", required = false) String status);

    @PostMapping("/v1/internal/users")
    UserResponse createUser(@RequestBody UserCreateRequest request);

    @DeleteMapping("/v1/internal/users/{userId}")
    void deleteUser(@PathVariable("userId") String userId);
}
