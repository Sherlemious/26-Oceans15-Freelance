package com.team26.freelance.job.dto;

import com.team26.freelance.job.model.JobCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class JobRequestDTO {

    @NotBlank
    private String title;

    @NotBlank
    private String description;

    @NotNull
    private JobCategory category;

    @NotNull
    private Double budgetMin;

    @NotNull
    private Double budgetMax;

    public JobRequestDTO() {}

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public JobCategory getCategory() { return category; }
    public void setCategory(JobCategory category) { this.category = category; }

    public Double getBudgetMin() { return budgetMin; }
    public void setBudgetMin(Double budgetMin) { this.budgetMin = budgetMin; }

    public Double getBudgetMax() { return budgetMax; }
    public void setBudgetMax(Double budgetMax) { this.budgetMax = budgetMax; }
}