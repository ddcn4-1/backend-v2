// module-queue/src/main/java/org/ddcn41/queue/repository/UserRepository.java

package org.ddcn41.queue.repository;

import org.ddcn41.queue.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
    /**
     *  username으로 userId 조회 (String 타입)
     */
    @Query("SELECT u.userId FROM User u WHERE u.username = :username")
    Optional<String> findUserIdByUsername(@Param("username") String username);
}