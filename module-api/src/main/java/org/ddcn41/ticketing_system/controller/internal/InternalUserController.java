package org.ddcn41.ticketing_system.controller.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.ddcn41.ticketing_system.common.dto.user.UserCreateRequest;
import org.ddcn41.ticketing_system.common.dto.user.UserResponse;
import org.ddcn41.ticketing_system.user.service.UserFacadeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/internal/users")
@RequiredArgsConstructor
@Tag(name = "Users internal", description = "APIs for user management")
public class InternalUserController {
    private final UserFacadeService userFacadeService;

    // 모든 유저 조회
    @GetMapping
    @Operation(summary = "List all users", description = "Lists all users")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content)
    })
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        List<UserResponse> users = userFacadeService.getAllUsers();
        return ResponseEntity.ok(users);
    }

    // 유저 생성
    @PostMapping
    @Operation(summary = "Create user", description = "Create new user")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "User created",
                    content = @Content(schema = @Schema(implementation = UserCreateRequest.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
            @ApiResponse(responseCode = "404", description = "Related resource not found", content = @Content)
    })
    public ResponseEntity<UserResponse> createUser(@RequestBody UserCreateRequest userCreateRequest) {
        UserResponse createdUser = userFacadeService.createUser(userCreateRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdUser);
    }

    // 유저 삭제
    @DeleteMapping("/{userId}")
    @Operation(summary = "Delete user", description = "Delete user")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "User deleted",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
            @ApiResponse(responseCode = "404", description = "Related resource not found", content = @Content)
    })
    public ResponseEntity<UserResponse> deleteUser(
            @Parameter(description = "User ID", required = true)
            @PathVariable String userId) {
        userFacadeService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }

    // 유저 검색
    @GetMapping("/search")
    @Operation(summary = "Search user", description = "Search using username, role, and status")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content)
    })
    public ResponseEntity<List<UserResponse>> searchUsers(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status) {

        List<UserResponse> users = userFacadeService.searchUsers(username, role, status);

        return ResponseEntity.ok(users);
    }

    // 유저 조회
    @GetMapping("/{userId}")
    @Operation(summary = "get user", description = "Get user by Id")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content)
    })
    public ResponseEntity<UserResponse> getUserById(
            @Parameter(description = "User ID", required = true)
            @PathVariable String userId) {

        UserResponse user = userFacadeService.getUserById(userId);

        return ResponseEntity.ok(user);
    }
}
