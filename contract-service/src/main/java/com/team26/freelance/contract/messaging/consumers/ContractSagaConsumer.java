package com.team26.freelance.contract.messaging.consumers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team26.freelance.contract.messaging.publishers.ContractSagaPublisher;
import com.team26.freelance.contract.config.ContractEventConfig;
import com.team26.freelance.contracts.events.ProposalAcceptedEvent;
import com.team26.freelance.contracts.events.ProposalCancelledEvent;
import com.team26.freelance.contracts.events.ProposalCompletedEvent;
import com.team26.freelance.contracts.events.SagaTopics;
import com.team26.freelance.contract.model.Contract;
import com.team26.freelance.contract.model.ContractStatus;
import com.team26.freelance.contract.service.ContractService;
import com.team26.freelance.contracts.dto.ContractDTO;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Component
public class ContractSagaConsumer {

    private static final Logger log = LoggerFactory.getLogger(ContractSagaConsumer.class);
    private static final String CORRELATION_ID_HEADER = "correlationId";
    private final ContractService contractService;
    private final ContractSagaPublisher contractSagaPublisher;
    private final ObjectMapper objectMapper;

    public ContractSagaConsumer(ContractService contractService, ContractSagaPublisher contractSagaPublisher, ObjectMapper objectMapper) {
        this.contractService = contractService;
        this.contractSagaPublisher = contractSagaPublisher;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = ContractEventConfig.CONTRACT_USER_SAGA_QUEUE,
                    arguments = {
                            @org.springframework.amqp.rabbit.annotation.Argument(name = "x-dead-letter-exchange", value = "contract.dlx"),
                            @org.springframework.amqp.rabbit.annotation.Argument(name = "x-dead-letter-routing-key", value = ContractEventConfig.CONTRACT_USER_SAGA_DLQ)
                    }),
            exchange = @Exchange(name = SagaTopics.PROPOSAL_EVENTS_EXCHANGE, type = "topic"),
            key = {SagaTopics.PROPOSAL_ACCEPTED, SagaTopics.PROPOSAL_COMPLETED, SagaTopics.PROPOSAL_CANCELLED}
    ), ackMode = "AUTO")
    public void handleProposalEvents(Message message) throws IOException {
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();
        bindMessageMdc(message, routingKey);
        try {
            log.info("Consuming contract proposal event routingKey={} correlationId={}", routingKey, MDC.get(CORRELATION_ID_HEADER));

            if (SagaTopics.PROPOSAL_ACCEPTED.equals(routingKey)) {
                ProposalAcceptedEvent event = objectMapper.readValue(message.getBody(), ProposalAcceptedEvent.class);
                putMdc("proposalId", event.proposalId());
                putMdc("jobId", event.jobId());
                handleProposalAccepted(event);
            } else if (SagaTopics.PROPOSAL_COMPLETED.equals(routingKey)) {
                ProposalCompletedEvent event = objectMapper.readValue(message.getBody(), ProposalCompletedEvent.class);
                putMdc("proposalId", event.proposalId());
                putMdc("jobId", event.jobId());
                putMdc("contractId", event.contractId());
                handleProposalCompleted(event);
            } else if (SagaTopics.PROPOSAL_CANCELLED.equals(routingKey)) {
                ProposalCancelledEvent event = objectMapper.readValue(message.getBody(), ProposalCancelledEvent.class);
                putMdc("proposalId", event.proposalId());
                putMdc("jobId", event.jobId());
                handleProposalCancelled(event);
            } else {
                throw new IllegalArgumentException("Unsupported proposal routing key: " + routingKey);
            }

            log.info("Processed contract proposal event routingKey={} correlationId={} proposalId={} contractId={}",
                    routingKey, MDC.get(CORRELATION_ID_HEADER), MDC.get("proposalId"), MDC.get("contractId"));
        } catch (Exception ex) {
            log.error("Failed contract proposal event routingKey={} correlationId={} proposalId={} contractId={} error={}",
                    routingKey, MDC.get(CORRELATION_ID_HEADER), MDC.get("proposalId"), MDC.get("contractId"), ex.getMessage(), ex);
            throw ex;
        } finally {
            clearMessageMdc();
        }
    }

    private void handleProposalAccepted(ProposalAcceptedEvent event) {
        Long proposalId = event.proposalId();
        if (proposalId == null) return;

        try {
            contractService.getActiveContractForProposal(proposalId);
            log.info("Active contract already exists for proposalId: {}. Skipping creation.", proposalId);
        } catch (ResponseStatusException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.info("No active contract for proposalId: {}. Creating one.", proposalId);
                Contract contract = new Contract();
                contract.setProposalId(proposalId);
                contract.setJobId(event.jobId());
                contract.setFreelancerId(event.freelancerId());
                contract.setClientId(event.clientId());
                contract.setAgreedAmount(event.bidAmount() != null ? event.bidAmount().doubleValue() : 0.0);
                contract.setStatus(ContractStatus.ACTIVE);
                contract.setStartDate(LocalDateTime.now());
                Contract saved = contractService.createContract(contract);
                putMdc("contractId", saved.getId());
            } else {
                throw e;
            }
        }
    }

    private void handleProposalCompleted(ProposalCompletedEvent event) {
        Long proposalId = event.proposalId();
        if (proposalId == null) return;

        try {
            ContractDTO activeContract = contractService.getActiveContractForProposal(proposalId);
            putMdc("contractId", activeContract.getId());
            Contract updateDetails = new Contract();
            updateDetails.setStatus(ContractStatus.COMPLETED);
            Contract saved = contractService.update(activeContract.getId(), updateDetails);
            log.info("Updated contract {} to COMPLETED for proposal {}", activeContract.getId(), proposalId);
        } catch (ResponseStatusException e) {
            log.warn("Received proposal.completed but no active contract found for proposalId: {}", proposalId);
        }
    }

    private void handleProposalCancelled(ProposalCancelledEvent event) {
        Long proposalId = event.proposalId();
        if (proposalId == null) return;

        // Try ACTIVE first, then COMPLETED (compensation case: payout failed after completion)
        ContractDTO targetContract = null;
        ContractStatus previousStatus = null;

        try {
            targetContract = contractService.getActiveContractForProposal(proposalId);
            putMdc("contractId", targetContract.getId());
            previousStatus = ContractStatus.ACTIVE;
        } catch (ResponseStatusException e) {
            if (e.getStatusCode() != HttpStatus.NOT_FOUND) {
                throw e;
            }
        }

        if (targetContract == null) {
            try {
                targetContract = contractService.getContractForProposalByStatus(proposalId, ContractStatus.COMPLETED);
                putMdc("contractId", targetContract.getId());
                previousStatus = ContractStatus.COMPLETED;
            } catch (ResponseStatusException e) {
                if (e.getStatusCode() != HttpStatus.NOT_FOUND) {
                    throw e;
                }
                log.warn("Received proposal.cancelled but no active or completed contract found for proposalId: {}. Nothing to terminate.", proposalId);
                return;
            }
        }

        try {
            Contract updateDetails = new Contract();
            updateDetails.setStatus(ContractStatus.TERMINATED);
            contractService.update(targetContract.getId(), updateDetails);
            contractSagaPublisher.publishContractCancelled(targetContract.getId(), proposalId);
            log.info("Updated contract {} from {} to TERMINATED and published events for proposal {}", targetContract.getId(), previousStatus, proposalId);
        } catch (ResponseStatusException e) {
            log.warn("Failed to terminate contract {} for proposalId: {}: {}", targetContract.getId(), proposalId, e.getMessage());
        }
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "contract.saga-listener",
                    arguments = {
                            @org.springframework.amqp.rabbit.annotation.Argument(name = "x-dead-letter-exchange", value = "contract.dlx"),
                            @org.springframework.amqp.rabbit.annotation.Argument(name = "x-dead-letter-routing-key", value = "contract.saga-listener.dlq")
                    }),
            exchange = @Exchange(name = SagaTopics.USER_EVENTS_EXCHANGE, type = "topic"),
            key = SagaTopics.USER_DEACTIVATED
    ), ackMode = "AUTO")
    public void handleUserDeactivated(Map<String, Object> payload) {
        log.info("Received user.deactivated event: {}", payload);
        // Optional bookkeeping: log deactivation against ACTIVE contracts
    }

    private void bindMessageMdc(Message message, String routingKey) {
        Object correlationIdHeader = message.getMessageProperties().getHeaders().get(CORRELATION_ID_HEADER);
        if (correlationIdHeader != null && !String.valueOf(correlationIdHeader).isBlank()) {
            MDC.put(CORRELATION_ID_HEADER, String.valueOf(correlationIdHeader));
        } else {
            MDC.put(CORRELATION_ID_HEADER, UUID.randomUUID().toString());
        }
        if (routingKey != null) {
            MDC.put("routingKey", routingKey);
        }
    }

    private void putMdc(String key, Object value) {
        if (value != null) {
            MDC.put(key, String.valueOf(value));
        }
    }

    private void clearMessageMdc() {
        MDC.remove(CORRELATION_ID_HEADER);
        MDC.remove("routingKey");
        MDC.remove("proposalId");
        MDC.remove("jobId");
        MDC.remove("contractId");
    }
}
