package org.ddcn41.ticketing_system.user.service;

import lombok.RequiredArgsConstructor;
import org.ddcn41.ticketing_system.user.dto.UserCreateRequestDto;
import org.ddcn41.ticketing_system.user.dto.UserResponseDto;
import org.ddcn41.ticketing_system.user.entity.User;
import org.ddcn41.ticketing_system.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private final CognitoIdentityProviderClient cognitoClient;

    @Value("${auth.cognito.user-pool-id}")
    private String userPoolId;

    //  모든 유저 조회
    public List<UserResponseDto> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::convertToResponseDto)
                .collect(Collectors.toList());
    }

    // 유저 생성
    @Transactional
    public UserResponseDto createUser(UserCreateRequestDto userCreateRequestDto) {
        String password = userCreateRequestDto.getPassword();

        // Cognito 이전 후 제거
        String passwordHash = encoder.encode(password);

        try {
            // 1. Cognito에 사용자 생성
            AdminCreateUserRequest cognitoRequest = AdminCreateUserRequest.builder()
                    .userPoolId(userPoolId)
                    .username(userCreateRequestDto.getUsername())
                    .userAttributes(
                            AttributeType.builder().name("email").value(userCreateRequestDto.getEmail()).build(),
                            AttributeType.builder().name("name").value(userCreateRequestDto.getName()).build()
                    )
                    .messageAction(MessageActionType.SUPPRESS)
                    .build();

            cognitoClient.adminCreateUser(cognitoRequest);

            AdminSetUserPasswordRequest adminSetUserPasswordRequest = AdminSetUserPasswordRequest.builder()
                    .userPoolId(userPoolId)
                    .username(userCreateRequestDto.getUsername())
                    .password(password)
                    .permanent(true)
                    .build();

            cognitoClient.adminSetUserPassword(adminSetUserPasswordRequest);

            // 2. Cognito 그룹 추가
            String groupName = userCreateRequestDto.getRole().toString().toLowerCase();
            AdminAddUserToGroupRequest request = AdminAddUserToGroupRequest.builder()
                    .userPoolId(userPoolId)
                    .username(userCreateRequestDto.getUsername())
                    .groupName(groupName)
                    .build();

            cognitoClient.adminAddUserToGroup(request);

            // 3. DB에 저장
            User user = User.builder()
                    .username(userCreateRequestDto.getUsername())
                    .email(userCreateRequestDto.getEmail())
                    .name(userCreateRequestDto.getName())
                    .passwordHash(passwordHash)
                    .phone(userCreateRequestDto.getPhone())
                    .role(userCreateRequestDto.getRole())
                    .build();

            User savedUser = userRepository.save(user);

            return convertToResponseDto(savedUser);

        } catch (UsernameExistsException e) {
            log.error("Username already exists: {}", userCreateRequestDto.getUsername());
            throw new RuntimeException("이미 존재하는 사용자명입니다.", e);
        } catch (CognitoIdentityProviderException e) {
            log.error("Cognito Create error: {}", e.awsErrorDetails().errorMessage());
            throw new RuntimeException("Cognito 사용자 생성 실패: " + e.awsErrorDetails().errorMessage(), e);
        }
    }

    // 유저 삭제
    @Transactional
    public void deleteUser(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + userId));

        try {
            // 1. Cognito에서 삭제
            AdminDeleteUserRequest request = AdminDeleteUserRequest.builder()
                    .userPoolId(userPoolId)
                    .username(user.getUsername())
                    .build();

            cognitoClient.adminDeleteUser(request);

            // 2. DB에서 삭제
            userRepository.deleteById(userId);

        } catch (UserNotFoundException e) {
            // Cognito에는 없지만 DB에는 있는 경우 -> DB만 삭제
            log.warn("User not found in Cognito, deleting from DB only: {}", user.getUsername());
            userRepository.deleteById(userId);
        } catch (CognitoIdentityProviderException e) {
            log.error("Cognito Delete error: {}", e.awsErrorDetails().errorMessage());
            throw new RuntimeException("Cognito 사용자 삭제 실패: " + e.awsErrorDetails().errorMessage(), e);
        }
    }

    // 유저 검색
    public List<UserResponseDto> searchUsers(String username, User.Role role, User.Status status) {
        List<UserResponseDto> result = getAllUsers();

        return result.stream()
                .filter(u -> username == null || username.trim().isEmpty() ||
                        u.getUsername().toLowerCase().contains(username.toLowerCase()))
                .filter(u -> role == null || u.getRole().equals(role))
                .filter(u -> status == null || u.getStatus().equals(status))
                .collect(Collectors.toList());
    }

    public String resolveUsernameFromEmailOrUsername(String usernameOrEmail) {
        if (usernameOrEmail.contains("@")) {
            // 이메일로 사용자 찾기
            User user = userRepository.findByEmail(usernameOrEmail)
                    .orElseThrow(() -> new UsernameNotFoundException("해당 이메일의 사용자를 찾을 수 없습니다: " + usernameOrEmail));
            return user.getUsername();
        } else {
            User user = userRepository.findByUsername(usernameOrEmail)
                    .orElseThrow(() -> new UsernameNotFoundException("해당 사용자명을 찾을 수 없습니다: " + usernameOrEmail));
            return user.getUsername();
        }
    }

    /**
     * 사용자 역할 정보 반환
     */
    public String getUserRole(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + username));
        return user.getRole().name();
    }

    /**
     * 사용자 정보 조회
     */
    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + username));
    }

    /**
     * 이메일로 사용자 정보 조회
     */
    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("해당 이메일의 사용자를 찾을 수 없습니다: " + email));
    }

    private UserResponseDto convertToResponseDto(User user) {
        return UserResponseDto.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .email(user.getEmail())
                .name(user.getName())
                .phone(user.getPhone())
                .role(user.getRole())
                .status(user.getStatus())
                .build();
    }

    @Transactional
    public User updateUserLoginTime(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + username));

        user.setLastLogin(LocalDateTime.now());
        return userRepository.save(user);
    }
}