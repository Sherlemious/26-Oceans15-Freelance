package com.team26.freelance.user.dto;

public class TopFreelancerDTO {
    private Long userId;
    private String name;
    private Double totalEarnings;
    private Long contractCount;

    public TopFreelancerDTO(Long userId, String name, Double totalEarnings, Long contractCount) {
        this.userId = userId;
        this.name = name;
        this.totalEarnings = totalEarnings;
        this.contractCount = contractCount;
    }

    public Long getUserId() { return userId; }
    public String getName() { return name; }
    public Double getTotalEarnings() { return totalEarnings; }
    public Long getContractCount() { return contractCount; }
}