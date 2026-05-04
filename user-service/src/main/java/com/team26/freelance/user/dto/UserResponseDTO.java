package com.team26.freelance.user.dto;

import com.team26.freelance.user.model.Role;
import com.team26.freelance.user.model.Status;
import com.team26.freelance.user.model.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class UserResponseDTO {
    private Long id;
    private String name;
    private String email;
    private String phone;
    private Role role;
    private Status status;
    private Map<String, Object> preferences;
    private LocalDateTime createdAt;
    private List<UserSkillResponseDTO> userSkills;

    public UserResponseDTO(Long id, String name, String email, String phone,
                           Role role, Status status, Map<String, Object> preferences,
                           LocalDateTime createdAt, List<UserSkillResponseDTO> userSkills) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.role = role;
        this.status = status;
        this.preferences = preferences;
        this.createdAt = createdAt;
        this.userSkills = userSkills;
    }

    public UserResponseDTO(User user) {
        this(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole(),
                user.getStatus(),
                user.getPreferences(),
                user.getCreatedAt(),
                mapUserSkills(user)
        );
    }

    public static UserResponseDTO fromUser(User user) {
        return builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .role(user.getRole())
                .status(user.getStatus())
                .preferences(user.getPreferences())
                .createdAt(user.getCreatedAt())
                .userSkills(mapUserSkills(user))
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public Role getRole() { return role; }
    public Status getStatus() { return status; }
    public Map<String, Object> getPreferences() { return preferences; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public List<UserSkillResponseDTO> getUserSkills() { return userSkills; }

    private static List<UserSkillResponseDTO> mapUserSkills(User user) {
        return user.getUserSkills().stream()
                .map(s -> UserSkillResponseDTO.builder()
                        .id(s.getId())
                        .skillName(s.getSkillName())
                        .category(s.getCategory())
                        .yearsOfExperience(s.getYearsOfExperience())
                        .proficiencyLevel(s.getProficiencyLevel())
                        .isPrimary(s.getIsPrimary())
                        .metadata(s.getMetadata())
                        .createdAt(s.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    public static class Builder {
        private Long id;
        private String name;
        private String email;
        private String phone;
        private Role role;
        private Status status;
        private Map<String, Object> preferences;
        private LocalDateTime createdAt;
        private List<UserSkillResponseDTO> userSkills;

        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder email(String email) {
            this.email = email;
            return this;
        }

        public Builder phone(String phone) {
            this.phone = phone;
            return this;
        }

        public Builder role(Role role) {
            this.role = role;
            return this;
        }

        public Builder status(Status status) {
            this.status = status;
            return this;
        }

        public Builder preferences(Map<String, Object> preferences) {
            this.preferences = preferences;
            return this;
        }

        public Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder userSkills(List<UserSkillResponseDTO> userSkills) {
            this.userSkills = userSkills;
            return this;
        }

        public UserResponseDTO build() {
            return new UserResponseDTO(
                    id,
                    name,
                    email,
                    phone,
                    role,
                    status,
                    preferences,
                    createdAt,
                    userSkills
            );
        }
    }
}
