package com.team26.freelance.proposal.model.neo4j;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

@Node("Freelancer")
public class FreelancerNode {
    @Id
    private Long userId;
    private String name;

    public FreelancerNode() {}

    public FreelancerNode(Long userId, String name) {
        this.userId = userId;
        this.name = name;
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}