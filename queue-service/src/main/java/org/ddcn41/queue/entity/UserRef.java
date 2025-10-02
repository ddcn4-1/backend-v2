package org.ddcn41.queue.entity;

import jakarta.persistence.Embeddable;

@Embeddable
public class UserRef {
    private Long userId;
    private String username;
}

