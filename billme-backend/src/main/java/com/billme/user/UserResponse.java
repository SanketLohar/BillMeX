package com.billme.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private Long id;
    private String email;
    private String role;
    private String profileImageUrl;
    private LocalDateTime createdAt;
    private boolean active;
    private String name;
    private LocalDateTime lastActive;
    private UserStats stats;
}
