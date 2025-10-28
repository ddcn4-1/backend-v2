// module-queue/src/main/java/org/ddcn41/queue/entity/User.java

package org.ddcn41.queue.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private String userId;

    @Column(unique = true, nullable = false, length = 100)
    private String username;
}