package com.team26.freelance.proposal.service;

import com.team26.freelance.contracts.events.ContractCreatedEvent;
import com.team26.freelance.contracts.events.ContractStatusChangedEvent;
import com.team26.freelance.contracts.events.PaymentCompletedEvent;
import com.team26.freelance.contracts.events.PaymentFailedEvent;
import com.team26.freelance.contracts.events.PaymentInitiatedEvent;
import com.team26.freelance.contracts.events.PaymentRefundedEvent;
import com.team26.freelance.contracts.events.SagaTopics;
import com.team26.freelance.proposal.config.ProposalEventConfig;
import com.team26.freelance.proposal.model.Proposal;
import com.team26.freelance.proposal.model.ProposalStatus;
import com.team26.freelance.proposal.repository.ProposalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Consumes saga feedback events from contract and payment services.
 * Mutates only proposal-service-owned state in freelancedb-proposals.
 * Failed messages are routed to proposal.saga-feedback.dlq via RabbitMQ DLX.
 */
@Service
public class SagaFeedbackConsumer {

    private static final Logger logger = LoggerFactory.getLogger(SagaFeedbackConsumer.class);

    private final ProposalRepository proposalRepository;
    private final ProposalEventPublisher proposalEventPublisher;
    private final ProposalCacheEvictionService cacheEvictionService;

    @Autowired
    public SagaFeedbackConsumer(ProposalRepository proposalRepository,
                                ProposalEventPublisher proposalEventPublisher,
                                ProposalCacheEvictionService cacheEvictionService) {
        this.proposalRepository = proposalRepository;
        this.proposalEventPublisher = proposalEventPublisher;
        this.cacheEvictionService = cacheEvictionService;
    }

    /**
     * Consumes contract.created event.
     * Links contractId into the Proposal record.
     */
    @RabbitListener(queues = ProposalEventConfig.PROPOSAL_SAGA_FEEDBACK_QUEUE)
    @Transactional
    public void consumeContractCreated(ContractCreatedEvent event,
                                       @Header(value = "correlationId", required = false) String correlationId,
                                       @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) {
        try {
            Long proposalId = event.proposalId();
            Long contractId = event.contractId();
            if (correlationId != null) MDC.put("correlationId", correlationId);
            if (routingKey != null) MDC.put("routingKey", routingKey);
            MDC.put("proposalId", proposalId.toString());
            logger.info("Consuming {} for proposalId={} correlationId={} routingKey={}", routingKey, proposalId, correlationId, routingKey);
            
            Proposal proposal = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proposal not found: " + proposalId));
            
            proposal.setContractId(contractId);
            proposalRepository.save(proposal);
            cacheEvictionService.evictProposalCaches(proposalId);
            
            logger.info("Processed {} for proposalId={} correlationId={} routingKey={}: linked contractId={}", routingKey, proposalId, correlationId, routingKey, contractId);
        } catch (Exception e) {
            logger.error("Failed to process {} for proposalId={} correlationId={} routingKey={}", routingKey, (event != null ? event.proposalId() : "-"), correlationId, routingKey, e);
            throw e;  // Rethrow to trigger DLQ routing
        } finally {
            MDC.remove("proposalId");
            MDC.remove("correlationId");
            MDC.remove("routingKey");
        }
    }

    /**
     * Consumes contract.status-changed event.
     * Logs the contract status transition (informational only).
     */
    @RabbitListener(queues = ProposalEventConfig.PROPOSAL_SAGA_FEEDBACK_QUEUE)
    public void consumeContractStatusChanged(ContractStatusChangedEvent event,
                                             @Header(value = "correlationId", required = false) String correlationId,
                                             @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) {
        try {
            Long contractId = event.contractId();
            String oldStatus = event.oldStatus();
            String newStatus = event.newStatus();
            if (correlationId != null) MDC.put("correlationId", correlationId);
            if (routingKey != null) MDC.put("routingKey", routingKey);
            logger.info("Consuming {} for contractId={} correlationId={} routingKey={}", routingKey, contractId, correlationId, routingKey);
            logger.info("Processed {} for contractId={} correlationId={} routingKey={}: {} -> {}", routingKey, contractId, correlationId, routingKey, oldStatus, newStatus);
        } catch (Exception e) {
            logger.error("Failed to process {} for contractId={} correlationId={} routingKey={}", routingKey, (event != null ? event.contractId() : "-"), correlationId, routingKey, e);
            throw e;  // Rethrow to trigger DLQ routing
        }
    }

    /**
     * Consumes payment.initiated event.
     * Marks proposal status = PAYMENT_PENDING.
     */
    @RabbitListener(queues = ProposalEventConfig.PROPOSAL_SAGA_FEEDBACK_QUEUE)
    @Transactional
    public void consumePaymentInitiated(PaymentInitiatedEvent event,
                                        @Header(value = "correlationId", required = false) String correlationId,
                                        @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) {
        try {
            Long proposalId = event.proposalId();
            Long payoutId = event.payoutId();
            if (correlationId != null) MDC.put("correlationId", correlationId);
            if (routingKey != null) MDC.put("routingKey", routingKey);
            MDC.put("proposalId", proposalId.toString());
            logger.info("Consuming {} for proposalId={} correlationId={} routingKey={}", routingKey, proposalId, correlationId, routingKey);
            
            Proposal proposal = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proposal not found: " + proposalId));
            
            ProposalStatus oldStatus = proposal.getStatus();
            proposal.setStatus(ProposalStatus.PAYMENT_PENDING);
            proposalRepository.save(proposal);
            cacheEvictionService.evictProposalCaches(proposalId);
            
            logger.info("Processed {} for proposalId={} correlationId={} routingKey={}: {} -> PAYMENT_PENDING (payoutId={})", routingKey, proposalId, correlationId, routingKey, oldStatus, payoutId);
        } catch (Exception e) {
            logger.error("Failed to process {} for proposalId={} correlationId={} routingKey={}", routingKey, (event != null ? event.proposalId() : "-"), correlationId, routingKey, e);
            throw e;  // Rethrow to trigger DLQ routing
        } finally {
            MDC.remove("proposalId");
            MDC.remove("correlationId");
            MDC.remove("routingKey");
        }
    }

    /**
     * Consumes payment.completed event.
     * Marks proposal status = PAID.
     */
    @RabbitListener(queues = ProposalEventConfig.PROPOSAL_SAGA_FEEDBACK_QUEUE)
    @Transactional
    public void consumePaymentCompleted(PaymentCompletedEvent event,
                                        @Header(value = "correlationId", required = false) String correlationId,
                                        @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) {
        try {
            Long proposalId = event.proposalId();
            Long payoutId = event.payoutId();
            if (correlationId != null) MDC.put("correlationId", correlationId);
            if (routingKey != null) MDC.put("routingKey", routingKey);
            MDC.put("proposalId", proposalId.toString());
            logger.info("Consuming {} for proposalId={} correlationId={} routingKey={}", routingKey, proposalId, correlationId, routingKey);
            
            Proposal proposal = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proposal not found: " + proposalId));
            
            ProposalStatus oldStatus = proposal.getStatus();
            proposal.setStatus(ProposalStatus.PAID);
            proposalRepository.save(proposal);
            cacheEvictionService.evictProposalCaches(proposalId);
            
            logger.info("Processed {} for proposalId={} correlationId={} routingKey={}: {} -> PAID (payoutId={})", routingKey, proposalId, correlationId, routingKey, oldStatus, payoutId);
        } catch (Exception e) {
            logger.error("Failed to process {} for proposalId={} correlationId={} routingKey={}", routingKey, (event != null ? event.proposalId() : "-"), correlationId, routingKey, e);
            throw e;  // Rethrow to trigger DLQ routing
        } finally {
            MDC.remove("proposalId");
            MDC.remove("correlationId");
            MDC.remove("routingKey");
        }
    }

    /**
     * Consumes payment.failed event.
     * Marks proposal status = PAYMENT_FAILED and triggers proposal.cancelled through the publisher.
     */
    @RabbitListener(queues = ProposalEventConfig.PROPOSAL_SAGA_FEEDBACK_QUEUE)
    @Transactional
    public void consumePaymentFailed(PaymentFailedEvent event,
                                     @Header(value = "correlationId", required = false) String correlationId,
                                     @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) {
        try {
            Long proposalId = event.proposalId();
            String reason = event.reason();
            if (correlationId != null) MDC.put("correlationId", correlationId);
            if (routingKey != null) MDC.put("routingKey", routingKey);
            MDC.put("proposalId", proposalId.toString());
            logger.info("Consuming {} for proposalId={} correlationId={} routingKey={}", routingKey, proposalId, correlationId, routingKey);
            
            Proposal proposal = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proposal not found: " + proposalId));
            
            ProposalStatus oldStatus = proposal.getStatus();
            proposal.setStatus(ProposalStatus.PAYMENT_FAILED);
            proposalRepository.save(proposal);
            cacheEvictionService.evictProposalCaches(proposalId);
            
            logger.info("Processed {} for proposalId={} correlationId={} routingKey={}: {} -> PAYMENT_FAILED (reason={})", routingKey, proposalId, correlationId, routingKey, oldStatus, reason);
            
            // Compensation path: publish proposal.cancelled
            proposalEventPublisher.publishProposalCancelled(
                proposalId,
                proposal.getJobId(),
                proposal.getFreelancerId(),
                "payment_failed: " + reason
            );
        } catch (Exception e) {
            logger.error("Failed to process {} for proposalId={} correlationId={} routingKey={}", routingKey, (event != null ? event.proposalId() : "-"), correlationId, routingKey, e);
            throw e;  // Rethrow to trigger DLQ routing
        } finally {
            MDC.remove("proposalId");
            MDC.remove("correlationId");
            MDC.remove("routingKey");
        }
    }

    /**
     * Consumes payment.refunded event.
     * Marks proposal status = REFUNDED.
     */
    @RabbitListener(queues = ProposalEventConfig.PROPOSAL_SAGA_FEEDBACK_QUEUE)
    @Transactional
    public void consumePaymentRefunded(PaymentRefundedEvent event,
                                       @Header(value = "correlationId", required = false) String correlationId,
                                       @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) {
        try {
            Long proposalId = event.proposalId();
            Long payoutId = event.payoutId();
            if (correlationId != null) MDC.put("correlationId", correlationId);
            if (routingKey != null) MDC.put("routingKey", routingKey);
            MDC.put("proposalId", proposalId.toString());
            logger.info("Consuming {} for proposalId={} correlationId={} routingKey={}", routingKey, proposalId, correlationId, routingKey);
            
            Proposal proposal = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proposal not found: " + proposalId));
            
            ProposalStatus oldStatus = proposal.getStatus();
            proposal.setStatus(ProposalStatus.REFUNDED);
            proposalRepository.save(proposal);
            cacheEvictionService.evictProposalCaches(proposalId);
            
            logger.info("Processed {} for proposalId={} correlationId={} routingKey={}: {} -> REFUNDED (payoutId={})", routingKey, proposalId, correlationId, routingKey, oldStatus, payoutId);
        } catch (Exception e) {
            logger.error("Failed to process {} for proposalId={} correlationId={} routingKey={}", routingKey, (event != null ? event.proposalId() : "-"), correlationId, routingKey, e);
            throw e;  // Rethrow to trigger DLQ routing
        } finally {
            MDC.remove("proposalId");
            MDC.remove("correlationId");
            MDC.remove("routingKey");
        }
    }
}
