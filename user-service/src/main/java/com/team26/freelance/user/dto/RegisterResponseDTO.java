package com.team26.freelance.user.dto;

public class RegisterResponseDTO {
    private String token;
    private Long expiresIn;

    public RegisterResponseDTO(String token, Long expiresIn) {
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