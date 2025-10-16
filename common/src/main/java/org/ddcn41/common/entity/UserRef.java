package org.ddcn41.common.entity;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * User 정보의 최소 참조 (임시)
 * MSA 전환 후 제거 예정
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserRef {
    private Long userId;
    private String username;

    // 필요시 추가 필드
    // private String email;
}