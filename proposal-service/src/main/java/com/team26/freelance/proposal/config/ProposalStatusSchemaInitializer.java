package com.team26.freelance.proposal.config;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ProposalStatusSchemaInitializer {

    private static final String[] M2_STATUSES = {
            "COMPLETING",
            "PAYMENT_PENDING",
            "PAID",
            "PAYMENT_FAILED",
            "REFUNDED"
    };

    private final JdbcTemplate jdbcTemplate;

    public ProposalStatusSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void ensureM2StatusesExist() {
        for (String status : M2_STATUSES) {
            jdbcTemplate.execute("ALTER TYPE proposal_status ADD VALUE IF NOT EXISTS '" + status + "'");
        }
    }
}
