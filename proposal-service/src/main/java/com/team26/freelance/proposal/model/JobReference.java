package com.team26.freelance.proposal.model;

import jakarta.persistence.Column;

/**
 * Local job snapshot used for proposal ownership checks and saga status updates.
/**
 * Deprecated placeholder retained for backward compatibility.
 * Proposal now stores jobId as a plain Long to avoid cross-service JPA relations.
 */
public class JobReference {

    private Long id;

    @Column(name = "client_id")
    private Long clientId;

    private String title;

    private String status;

    public JobReference() {
    }

    public JobReference(Long id) {
        this.id = id;
    }

    public JobReference(Long id, Long clientId, String title, String status) {
        this.id = id;
        this.clientId = clientId;
        this.title = title;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getClientId() {
        return clientId;
    }

    public void setClientId(Long clientId) {
        this.clientId = clientId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
