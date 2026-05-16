package com.team26.freelance.contracts.events;

public record JobRatedEvent(Long jobId, Long contractId, Double rating, Long ratedBy) {
}
