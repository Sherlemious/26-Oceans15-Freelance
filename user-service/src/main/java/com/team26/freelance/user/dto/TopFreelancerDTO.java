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

    public static Builder builder() {
        return new Builder();
    }

    public Long getUserId() { return userId; }
    public String getName() { return name; }
    public Double getTotalEarnings() { return totalEarnings; }
    public Long getContractCount() { return contractCount; }

    public static class Builder {
        private Long userId;
        private String name;
        private Double totalEarnings;
        private Long contractCount;

        public Builder userId(Long userId) {
            this.userId = userId;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder totalEarnings(Double totalEarnings) {
            this.totalEarnings = totalEarnings;
            return this;
        }

        public Builder contractCount(Long contractCount) {
            this.contractCount = contractCount;
            return this;
        }

        public TopFreelancerDTO build() {
            return new TopFreelancerDTO(userId, name, totalEarnings, contractCount);
        }
    }
}
