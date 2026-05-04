package com.team26.freelance.user.dto;

import com.team26.freelance.user.model.ProficiencyLevel;
import java.util.Map;

public class UserProfileSkillDTO {
    private String skillName;
    private String category;
    private Integer yearsOfExperience;
    private ProficiencyLevel proficiencyLevel;
    private Boolean isPrimary;
    private Map<String, Object> metadata;

    public UserProfileSkillDTO(String skillName, String category, Integer yearsOfExperience,
                               ProficiencyLevel proficiencyLevel, Boolean isPrimary,
                               Map<String, Object> metadata) {
        this.skillName = skillName;
        this.category = category;
        this.yearsOfExperience = yearsOfExperience;
        this.proficiencyLevel = proficiencyLevel;
        this.isPrimary = isPrimary;
        this.metadata = metadata;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getSkillName() { return skillName; }
    public String getCategory() { return category; }
    public Integer getYearsOfExperience() { return yearsOfExperience; }
    public ProficiencyLevel getProficiencyLevel() { return proficiencyLevel; }
    public Boolean getIsPrimary() { return isPrimary; }
    public Map<String, Object> getMetadata() { return metadata; }

    public static class Builder {
        private String skillName;
        private String category;
        private Integer yearsOfExperience;
        private ProficiencyLevel proficiencyLevel;
        private Boolean isPrimary;
        private Map<String, Object> metadata;

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

        public UserProfileSkillDTO build() {
            return new UserProfileSkillDTO(
                    skillName,
                    category,
                    yearsOfExperience,
                    proficiencyLevel,
                    isPrimary,
                    metadata
            );
        }
    }
}
