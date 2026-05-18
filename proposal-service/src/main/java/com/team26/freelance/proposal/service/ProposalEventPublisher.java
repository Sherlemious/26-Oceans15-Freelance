package com.team26.freelance.proposal.service;

import com.team26.freelance.contracts.events.SagaTopics;
import com.team26.freelance.contracts.events.ProposalAcceptedEvent;
import com.team26.freelance.contracts.events.ProposalCompletedEvent;
import com.team26.freelance.contracts.events.ProposalCancelledEvent;
import com.team26.freelance.contracts.events.ProposalWithdrawnEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Publishes proposal lifecycle events to RabbitMQ.
 * All events published to proposal.events exchange.
 */
@Service
public class ProposalEventPublisher {

    private static final Logger logger = LoggerFactory.getLogger(ProposalEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    @Autowired
    public ProposalEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Publishes proposal.accepted event after proposal is accepted.
     * Payload: {proposalId, jobId, freelancerId, bidAmount}
     */
    public void publishProposalAccepted(Long proposalId, Long jobId, Long freelancerId, BigDecimal bidAmount) {
        ProposalAcceptedEvent event = new ProposalAcceptedEvent(proposalId, jobId, freelancerId, bidAmount);
        String correlationId = MDC.get("correlationId");
        
        logger.info("Publishing proposal.accepted event for proposalId={}", proposalId);
        rabbitTemplate.convertAndSend(
            SagaTopics.PROPOSAL_EVENTS_EXCHANGE,
            SagaTopics.PROPOSAL_ACCEPTED,
            event,
            message -> {
                if (correlationId != null) {
                    message.getMessageProperties().setHeader("correlationId", correlationId);
                }
                return message;
            }
        );
    }

    /**
     * Publishes proposal.completed event after proposal work is completed (saga trigger).
     * Payload: {proposalId, jobId, freelancerId, contractId, agreedAmount}
     */
    public void publishProposalCompleted(Long proposalId, Long jobId, Long freelancerId, Long contractId, BigDecimal agreedAmount) {
        ProposalCompletedEvent event = new ProposalCompletedEvent(proposalId, jobId, freelancerId, contractId, agreedAmount);
        String correlationId = MDC.get("correlationId");
        
        logger.info("Publishing proposal.completed event for proposalId={}", proposalId);
        rabbitTemplate.convertAndSend(
            SagaTopics.PROPOSAL_EVENTS_EXCHANGE,
            SagaTopics.PROPOSAL_COMPLETED,
            event,
            message -> {
                if (correlationId != null) {
                    message.getMessageProperties().setHeader("correlationId", correlationId);
                }
                return message;
            }
        );
    }

    /**
     * Publishes proposal.cancelled event as compensation path (e.g., payment.failed consumed).
     * Payload: {proposalId, jobId, freelancerId, reason}
     */
    public void publishProposalCancelled(Long proposalId, Long jobId, Long freelancerId, String reason) {
        ProposalCancelledEvent event = new ProposalCancelledEvent(proposalId, jobId, freelancerId, reason);
        String correlationId = MDC.get("correlationId");
        
        logger.info("Publishing proposal.cancelled event for proposalId={} with reason={}", proposalId, reason);
        rabbitTemplate.convertAndSend(
            SagaTopics.PROPOSAL_EVENTS_EXCHANGE,
            SagaTopics.PROPOSAL_CANCELLED,
            event,
            message -> {
                if (correlationId != null) {
                    message.getMessageProperties().setHeader("correlationId", correlationId);
                }
                return message;
            }
        );
    }

    /**
     * Publishes proposal.withdrawn event after proposal is withdrawn.
     * Payload: {proposalId, jobId, freelancerId}
     */
    public void publishProposalWithdrawn(Long proposalId, Long jobId, Long freelancerId) {
        ProposalWithdrawnEvent event = new ProposalWithdrawnEvent(proposalId, jobId, freelancerId);
        String correlationId = MDC.get("correlationId");
        
        logger.info("Publishing proposal.withdrawn event for proposalId={}", proposalId);
        rabbitTemplate.convertAndSend(
            SagaTopics.PROPOSAL_EVENTS_EXCHANGE,
            SagaTopics.PROPOSAL_WITHDRAWN,
            event,
            message -> {
                if (correlationId != null) {
                    message.getMessageProperties().setHeader("correlationId", correlationId);
                }
                return message;
            }
        );
    }
}
