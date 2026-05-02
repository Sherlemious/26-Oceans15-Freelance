package com.team26.freelance.user.dto;

import com.team26.freelance.user.model.Role;

public class LoginResponseDTO {
    private String token;
    private Long userId;
    private Role role;

    public LoginResponseDTO(String token, Long userId, Role role) {
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
