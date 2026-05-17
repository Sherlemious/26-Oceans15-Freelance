package com.team26.freelance.proposal.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Read-only reference to the shared jobs catalog table for FK constraint on proposals.job_id.
 */
@Entity
@Table(name = "jobs")
public class JobReference {

    @Id
    private Long id;

    public JobReference() {
    }

    public JobReference(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
