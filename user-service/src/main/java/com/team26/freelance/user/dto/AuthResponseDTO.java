package com.team26.freelance.user.dto;

public class AuthResponseDTO {
    private String token;
    private Long expiresIn;

    public AuthResponseDTO(String token, Long expiresIn) {
        this.token = token;
        this.expiresIn = expiresIn;
    }

    public String getToken() {
        return token;
    }

    public Long getExpiresIn() {
        return expiresIn;
    }
}
