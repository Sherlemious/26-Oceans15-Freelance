package com.team26.freelance.job.dto;

public class JobSearchResultDTO {


    private Long id;
    private String title;
    private String description;
    private String category;
    private Double budgetMin;
    private Double budgetMax;
    private float score;

    public JobSearchResultDTO() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Double getBudgetMin() {
        return budgetMin;
    }

    public void setBudgetMin(Double budgetMin) {
        this.budgetMin = budgetMin;
    }

    public Double getBudgetMax() {
        return budgetMax;
    }

    public void setBudgetMax(Double budgetMax) {
        this.budgetMax = budgetMax;
    }

    public float getScore() {
        return score;
    }

    public void setScore(float score) {
        this.score = score;
    }

    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private final JobSearchResultDTO dto = new JobSearchResultDTO();
        
        public Builder id(Long id) {
            dto.setId(id);
            return this;
        }
        
        public Builder title(String title) {
            dto.setTitle(title);
            return this;
        }
        
        public Builder description(String description) {
            dto.setDescription(description);
            return this;
        }
        
        public Builder category(String category) {
            dto.setCategory(category);
            return this;
        }
        
        public Builder budgetMin(Double budgetMin) {
            dto.setBudgetMin(budgetMin);
            return this;
        }
        
        public Builder budgetMax(Double budgetMax) {
            dto.setBudgetMax(budgetMax);
            return this;
        }
        
        public Builder score(float score) {
            dto.setScore(score);
            return this;
        }
        
        public JobSearchResultDTO build() {
            return dto;
        }
    }
}
