package com.team26.freelance.job.events;

public record JobRatedEvent(Long jobId, Long contractId, Double rating, Long ratedBy) {}