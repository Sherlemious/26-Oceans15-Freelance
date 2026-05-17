package com.team26.freelance.contracts.events;

/**
 * Single source of truth for RabbitMQ exchange names and routing keys
 * used across all saga participants. Mirrors the event map in M3 §2.9.
 *
 * Used by publishers via rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, event)
 * and by consumers via @RabbitListener bindings, so compile-time
 * mismatch between producer and consumer becomes impossible.
 */
public final class SagaTopics {

    public static final String USER_EVENTS_EXCHANGE = "user.events";
    public static final String JOB_EVENTS_EXCHANGE = "job.events";
    public static final String PROPOSAL_EVENTS_EXCHANGE = "proposal.events";
    public static final String CONTRACT_EVENTS_EXCHANGE = "contract.events";
    public static final String PAYMENT_EVENTS_EXCHANGE = "payment.events";

    public static final String USER_REGISTERED = "user.registered";
    public static final String USER_DEACTIVATED = "user.deactivated";

    public static final String JOB_STATUS_CHANGED = "job.status-changed";
    public static final String JOB_RATED = "job.rated";
    public static final String JOB_CLOSED = "job.closed";

    public static final String PROPOSAL_ACCEPTED = "proposal.accepted";
    public static final String PROPOSAL_COMPLETED = "proposal.completed";
    public static final String PROPOSAL_CANCELLED = "proposal.cancelled";
    public static final String PROPOSAL_WITHDRAWN = "proposal.withdrawn";

    public static final String CONTRACT_CREATED = "contract.created";
    public static final String CONTRACT_STATUS_CHANGED = "contract.status-changed";
    public static final String CONTRACT_CANCELLED = "contract.cancelled";

    public static final String PAYMENT_INITIATED = "payment.initiated";
    public static final String PAYMENT_COMPLETED = "payment.completed";
    public static final String PAYMENT_FAILED = "payment.failed";
    public static final String PAYMENT_REFUNDED = "payment.refunded";

    private SagaTopics() {
    }
}
