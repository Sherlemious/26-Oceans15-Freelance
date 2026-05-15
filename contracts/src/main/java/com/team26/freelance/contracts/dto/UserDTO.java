package com.team26.freelance.contracts.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class UserDTO {
    private Long id;
    private String name;
    private String email;
    private String phone;
    private String role;
    private String status;
    private Map<String, Object> preferences;
    private LocalDateTime createdAt;
    private List<Object> userSkills;

    public UserDTO() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Map<String, Object> getPreferences() {
        return preferences;
    }

    public void setPreferences(Map<String, Object> preferences) {
        this.preferences = preferences;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<Object> getUserSkills() {
        return userSkills;
    }

    public void setUserSkills(List<Object> userSkills) {
        this.userSkills = userSkills;
    }
}
