package com.team26.freelance.user.dto;

import com.team26.freelance.user.model.ProficiencyLevel;
import java.time.LocalDateTime;
import java.util.Map;

public class UserSkillResponseDTO {
    private Long id;
    private String skillName;
    private String category;
    private Integer yearsOfExperience;
    private ProficiencyLevel proficiencyLevel;
    private Boolean isPrimary;
    private Map<String, Object> metadata;
    private LocalDateTime createdAt;

    public UserSkillResponseDTO(Long id, String skillName, String category,
                                Integer yearsOfExperience, ProficiencyLevel proficiencyLevel,
                                Boolean isPrimary, Map<String, Object> metadata,
                                LocalDateTime createdAt) {
        this.id = id;
        this.skillName = skillName;
        this.category = category;
        this.yearsOfExperience = yearsOfExperience;
        this.proficiencyLevel = proficiencyLevel;
        this.isPrimary = isPrimary;
        this.metadata = metadata;
        this.createdAt = createdAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Long getId() { return id; }
    public String getSkillName() { return skillName; }
    public String getCategory() { return category; }
    public Integer getYearsOfExperience() { return yearsOfExperience; }
    public ProficiencyLevel getProficiencyLevel() { return proficiencyLevel; }
    public Boolean getIsPrimary() { return isPrimary; }
    public Map<String, Object> getMetadata() { return metadata; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public static class Builder {
        private Long id;
        private String skillName;
        private String category;
        private Integer yearsOfExperience;
        private ProficiencyLevel proficiencyLevel;
        private Boolean isPrimary;
        private Map<String, Object> metadata;
        private LocalDateTime createdAt;

        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        public Builder skillName(String skillName) {
            this.skillName = skillName;
            return this;
        }

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        public Builder yearsOfExperience(Integer yearsOfExperience) {
            this.yearsOfExperience = yearsOfExperience;
            return this;
        }

        public Builder proficiencyLevel(ProficiencyLevel proficiencyLevel) {
            this.proficiencyLevel = proficiencyLevel;
            return this;
        }

        public Builder isPrimary(Boolean isPrimary) {
            this.isPrimary = isPrimary;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public UserSkillResponseDTO build() {
            return new UserSkillResponseDTO(
                    id,
                    skillName,
                    category,
                    yearsOfExperience,
                    proficiencyLevel,
                    isPrimary,
                    metadata,
                    createdAt
            );
        }
    }
}
