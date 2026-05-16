package com.team26.freelance.contracts.events;

public record JobStatusChangedEvent(Long jobId, String oldStatus, String newStatus) {
}
