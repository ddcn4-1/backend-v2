package org.ddcn41.ticketing_system.user.repository;

import org.ddcn41.ticketing_system.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByUsername(String username);

    // 이메일 로그인
    Optional<User> findByEmail(String email);

    // 회원가입 기능을 안 쓰더라도, 중복 체크가 필요하면 유지
    boolean existsByUsername(String username);
}
