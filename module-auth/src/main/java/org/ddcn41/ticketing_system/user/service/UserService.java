package org.ddcn41.ticketing_system.user.service;

import lombok.RequiredArgsConstructor;
import org.ddcn41.ticketing_system.common.dto.user.UserCreateRequest;
import org.ddcn41.ticketing_system.common.exception.BusinessException;
import org.ddcn41.ticketing_system.common.exception.ErrorCode;
import org.ddcn41.ticketing_system.user.entity.User;
import org.ddcn41.ticketing_system.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

    // 유저 생성
    @Transactional
    public User createUser(UserCreateRequest request) {
        // 중복 확인
        if (userRepository.existsByUsername(request.getUsername()))
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS);

        User user = User.builder()
                .userId(request.getUserId())
                .username(request.getUsername())
                .email(request.getEmail())
                .name(request.getName())
                .phone(request.getPhone())
                .role(User.Role.valueOf(request.getRole()))
                .build();

        return userRepository.save(user);
    }

    // 유저 삭제
    @Transactional
    public void deleteUser(String userId) {
        // 존재 확인
        userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "userId: " + userId));

        userRepository.deleteById(userId);
    }

    /**
     * 사용자 정보 조회
     */
    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "username: " + username));
    }

    /**
     * 이메일로 사용자 정보 조회
     */
    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "email: " + email));
    }

    /**
     * User ID로 사용자 정보 조회
     */
    public User findById(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "userId: " + userId));
    }

    /**
     * 전체 사용자 정보 조회
     */
    public List<User> findAll() {
        return userRepository.findAll();
    }

    // v1 Auth Legacy
    public String resolveUsernameFromEmailOrUsername(String usernameOrEmail) {
        if (usernameOrEmail.contains("@")) {
            // 이메일로 사용자 찾기
            User user = userRepository.findByEmail(usernameOrEmail)
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "usernameOrEmail: " + usernameOrEmail));
            return user.getUsername();
        } else {
            User user = userRepository.findByUsername(usernameOrEmail)
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "usernameOrEmail: " + usernameOrEmail));
            return user.getUsername();
        }
    }

    // v1 Auth Legacy
    @Transactional
    public User updateUserLoginTime(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "username: " + username));

        user.setLastLogin(LocalDateTime.now());
        return userRepository.save(user);
    }
}