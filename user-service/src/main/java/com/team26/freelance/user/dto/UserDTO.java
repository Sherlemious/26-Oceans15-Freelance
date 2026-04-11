package com.team26.freelance.user.dto;

import com.team26.freelance.user.model.UserRole;
import com.fasterxml.jackson.databind.JsonNode;

public class UserDTO {
    
    private Long id;
    private String name;
    private String email;
    private UserRole role;
    private String phone;
    private String bio;
    private JsonNode preferences;
    
    // Constructors
    public UserDTO() {}
    
    public UserDTO(Long id, String name, String email, UserRole role) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.role = role;
    }
    
    public UserDTO(Long id, String name, String email, UserRole role, String phone, String bio) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.role = role;
        this.phone = phone;
        this.bio = bio;
    }
    
    public UserDTO(Long id, String name, String email, UserRole role, String phone, String bio, JsonNode preferences) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.role = role;
        this.phone = phone;
        this.bio = bio;
        this.preferences = preferences;
    }
    
    // Getters and Setters
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
    
    public UserRole getRole() {
        return role;
    }
    
    public void setRole(UserRole role) {
        this.role = role;
    }
    
    public String getPhone() {
        return phone;
    }
    
    public void setPhone(String phone) {
        this.phone = phone;
    }
    
    public String getBio() {
        return bio;
    }
    
    public void setBio(String bio) {
        this.bio = bio;
    }
    
    public JsonNode getPreferences() {
        return preferences;
    }
    
    public void setPreferences(JsonNode preferences) {
        this.preferences = preferences;
    }
}
