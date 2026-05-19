package com.team26.freelance.proposal.model;

/**
 * Deprecated placeholder retained for backward compatibility.
 * Proposal now stores jobId as a plain Long to avoid cross-service JPA relations.
 */
public class JobReference {

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
