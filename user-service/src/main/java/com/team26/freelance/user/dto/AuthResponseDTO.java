package com.team26.freelance.user.dto;

import com.team26.freelance.user.model.Role;

public class AuthResponseDTO {
    private String token;
    private Long userId;
    private Role role;

    public AuthResponseDTO(String token, Long userId, Role role) {
        this.token = token;
        this.userId = userId;
        this.role = role;
    }

    public String getToken() {
        return token;
    }

    public Long getUserId() {
        return userId;
    }

    public Role getRole() {
        return role;
    }
}
