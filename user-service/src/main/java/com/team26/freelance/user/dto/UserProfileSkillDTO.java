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

    public String getSkillName() { return skillName; }
    public String getCategory() { return category; }
    public Integer getYearsOfExperience() { return yearsOfExperience; }
    public ProficiencyLevel getProficiencyLevel() { return proficiencyLevel; }
    public Boolean getIsPrimary() { return isPrimary; }
    public Map<String, Object> getMetadata() { return metadata; }
}
