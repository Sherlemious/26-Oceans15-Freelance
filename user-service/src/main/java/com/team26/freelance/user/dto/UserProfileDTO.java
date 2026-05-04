package com.team26.freelance.user.dto;

import java.util.List;
import java.util.Map;

public class UserProfileDTO {
    private Long userId;
    private String name;
    private String email;
    private String phone;
    private Map<String, Object> preferences;
    private List<UserProfileSkillDTO> skills;
    private Integer totalSkills;

    public UserProfileDTO(Long userId, String name, String email, String phone,
                          Map<String, Object> preferences, List<UserProfileSkillDTO> skills,
                          Integer totalSkills) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.preferences = preferences;
        this.skills = skills;
        this.totalSkills = totalSkills;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Long getUserId() { return userId; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public Map<String, Object> getPreferences() { return preferences; }
    public List<UserProfileSkillDTO> getSkills() { return skills; }
    public Integer getTotalSkills() { return totalSkills; }

    public static class Builder {
        private Long userId;
        private String name;
        private String email;
        private String phone;
        private Map<String, Object> preferences;
        private List<UserProfileSkillDTO> skills;
        private Integer totalSkills;

        public Builder userId(Long userId) {
            this.userId = userId;
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

        public Builder preferences(Map<String, Object> preferences) {
            this.preferences = preferences;
            return this;
        }

        public Builder skills(List<UserProfileSkillDTO> skills) {
            this.skills = skills;
            return this;
        }

        public Builder totalSkills(Integer totalSkills) {
            this.totalSkills = totalSkills;
            return this;
        }

        public UserProfileDTO build() {
            return new UserProfileDTO(userId, name, email, phone, preferences, skills, totalSkills);
        }
    }
}
