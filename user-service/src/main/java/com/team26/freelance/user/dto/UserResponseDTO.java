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

    public UserResponseDTO(User user) {
        this.id = user.getId();
        this.name = user.getName();
        this.email = user.getEmail();
        this.phone = user.getPhone();
        this.role = user.getRole();
        this.status = user.getStatus();
        this.preferences = user.getPreferences();
        this.createdAt = user.getCreatedAt();
        this.userSkills = user.getUserSkills().stream()
                .map(s -> new UserSkillResponseDTO(
                        s.getId(), s.getSkillName(), s.getCategory(),
                        s.getYearsOfExperience(), s.getProficiencyLevel(),
                        s.getIsPrimary(), s.getMetadata(), s.getCreatedAt()))
                .collect(Collectors.toList());
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
    //s1-f2
}