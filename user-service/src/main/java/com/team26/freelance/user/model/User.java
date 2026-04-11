package com.team26.freelance.user.model;

import jakarta.persistence.*;
import org.hibernate.annotations.Type;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Entity
@Table(name = "users")
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String name;
    
    @Column(nullable = false, unique = true)
    private String email;
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private UserRole role;
    
    @Column
    private String phone;
    
    @Column
    private String bio;
    
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb", nullable = true)
    private JsonNode preferences;
    
    // Constructors
    public User() {}
    
    public User(String name, String email, UserRole role) {
        this.name = name;
        this.email = email;
        this.role = role;
    }
    
    public User(String name, String email, UserRole role, String phone, String bio) {
        this.name = name;
        this.email = email;
        this.role = role;
        this.phone = phone;
        this.bio = bio;
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
