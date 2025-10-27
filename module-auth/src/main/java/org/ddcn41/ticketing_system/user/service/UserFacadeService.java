package org.ddcn41.ticketing_system.user.service;

import lombok.RequiredArgsConstructor;
import org.ddcn41.ticketing_system.common.dto.user.UserCreateRequest;
import org.ddcn41.ticketing_system.common.dto.user.UserResponse;
import org.ddcn41.ticketing_system.common.exception.BusinessException;
import org.ddcn41.ticketing_system.common.exception.ErrorCode;
import org.ddcn41.ticketing_system.user.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UsernameExistsException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserFacadeService {
    private static final Logger log = LoggerFactory.getLogger(UserFacadeService.class);

    private final UserService userService;
    private final UserCognitoService userCognitoService;

    // 유저 생성
    public UserResponse createUser(UserCreateRequest request) {
        User savedUser = null;
        String cognitoSub = null;

        try {
            cognitoSub = userCognitoService.createCognitoUser(
                    request.getUsername(),
                    request.getEmail(),
                    request.getName()
            );
            userCognitoService.addUserToGroup(request.getUsername(), request.getRole().toLowerCase());
            userCognitoService.changePassword(request.getUsername(), request.getPassword());

            request.setUserId(cognitoSub);
            savedUser = userService.createUser(request);

            return toResponse(savedUser);

        } catch (UsernameExistsException e) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS);

        } catch (CognitoIdentityProviderException e) {
            throw new BusinessException(ErrorCode.COGNITO_USER_CREATE_FAILED, e.awsErrorDetails().errorMessage());

        } catch (Exception e) {
            // Cognito에서 실패한 경우
            if (cognitoSub == null) {
                throw new BusinessException(ErrorCode.COGNITO_USER_CREATE_FAILED);
            }
            // Cognito는 성공했지만 DB 저장 실패한 경우
            if (savedUser == null) {
                try {
                    // Cognito 롤백
                    userCognitoService.deleteCognitoUser(request.getUsername());
                } catch (Exception rollbackException) {
                    throw new BusinessException(ErrorCode.COGNITO_USER_CREATE_FAILED, "Cognito 롤백 실패");
                }
            }

            throw new BusinessException(ErrorCode.USER_CREATE_FAILED, e.getMessage());
        }
    }

    // 유저 삭제
    public void deleteUser(String userId) {
        User user = userService.findById(userId);

        try {
            userCognitoService.deleteCognitoUser(user.getUsername());

            userService.deleteUser(userId);

        } catch (UserNotFoundException e) {
            // Cognito에는 없지만 DB에는 있는 경우 -> DB 삭제
            log.warn("User not found in Cognito, deleting from DB only: {}", user.getUsername());
            userService.deleteUser(userId);

        } catch (CognitoIdentityProviderException e) {
            throw new BusinessException(ErrorCode.COGNITO_USER_DELETE_FAILED, e.awsErrorDetails().errorMessage());

        } catch (Exception e) {
            throw new BusinessException(ErrorCode.USER_DELETE_FAILED, e.getMessage());
        }
    }

    // 모든 유저 조회
    public List<UserResponse> getAllUsers() {
        List<User> users = userService.findAll();

        return users.stream()
                .map(this::toResponse)
                .toList();
    }

    // 유저 목록 검색
    public List<UserResponse> searchUsers(String username, String role, String status) {
        List<User> users = userService.findAll();

        return users.stream()
                .map(this::toResponse)
                .filter(u -> username == null || username.trim().isEmpty() ||
                        u.getUsername().toLowerCase().contains(username.toLowerCase()))
                .filter(u -> role == null || u.getRole().equals(role))
                .filter(u -> status == null || u.getStatus().equals(status))
                .toList();
    }

    // 유저 조회
    public UserResponse getUserById(String userId) {
        User user = userService.findById(userId);

        return toResponse(user);
    }

    // 유저 조회
    public UserResponse getUserByUsername(String username) {
        User user = userService.findByUsername(username);

        return toResponse(user);
    }

    private UserResponse toResponse(User user) {
        return UserResponse.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .email(user.getEmail())
                .name(user.getName())
                .phone(user.getPhone())
                .role(user.getRole().toString())
                .status(user.getStatus().toString())
                .build();
    }

}
