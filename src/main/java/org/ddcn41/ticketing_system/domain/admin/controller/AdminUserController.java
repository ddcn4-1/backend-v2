package org.ddcn41.ticketing_system.domain.admin.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.ddcn41.ticketing_system.domain.user.dto.UserCreateRequestDto;
import org.ddcn41.ticketing_system.domain.user.dto.UserResponseDto;
import org.ddcn41.ticketing_system.domain.user.entity.User;
import org.ddcn41.ticketing_system.domain.user.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Users", description = "APIs for user management")
public class AdminUserController {

    private final UserService userService;

    // 모든 유저 조회
    @GetMapping
    @Operation(summary = "List all users", description = "Lists all users")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = UserResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content)
    })
    public ResponseEntity<List<UserResponseDto>> getAllUsers() {
        List<UserResponseDto> users = userService.getAllUsers();
        return ResponseEntity.ok(users);
    }

    // 유저 생성
    @PostMapping
    @Operation(summary = "Create user", description = "Create new user")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "User created",
                    content = @Content(schema = @Schema(implementation = UserCreateRequestDto.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
            @ApiResponse(responseCode = "404", description = "Related resource not found", content = @Content)
    })
    public ResponseEntity<UserResponseDto> createUser(@RequestBody UserCreateRequestDto userCreateRequestDto) {
        UserResponseDto createdUser = userService.createUser(userCreateRequestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdUser);
    }

    // 유저 삭제
    @DeleteMapping("/{userId}")
    @Operation(summary = "Delete user", description = "Delete user")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "User deleted",
                    content = @Content(schema = @Schema(implementation = UserResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
            @ApiResponse(responseCode = "404", description = "Related resource not found", content = @Content)
    })
    public ResponseEntity<UserResponseDto> deleteUser(
            @Parameter(description = "User ID", required = true)
            @PathVariable Long userId) {
        userService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }

    // 유저 검색
    @GetMapping("/search")
    @Operation(summary = "Search user", description = "Search using username, role, and status")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = UserResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content)
    })
    public ResponseEntity<List<UserResponseDto>> searchUsers(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) User.Role role,
            @RequestParam(required = false) User.Status status) {

        List<UserResponseDto> users = userService.searchUsers(username, role, status);

        return ResponseEntity.ok(users);
    }
}
