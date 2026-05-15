package com.team26.freelance.job.events;

public record JobStatusChangedEvent(Long jobId, String oldStatus, String newStatus) {}