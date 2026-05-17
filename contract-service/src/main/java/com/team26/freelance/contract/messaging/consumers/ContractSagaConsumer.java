package com.team26.freelance.contract.messaging.consumers;

import com.team26.freelance.contract.messaging.publishers.ContractSagaPublisher;
import com.team26.freelance.contracts.events.SagaTopics;
import com.team26.freelance.contract.model.Contract;
import com.team26.freelance.contract.model.ContractStatus;
import com.team26.freelance.contract.service.ContractService;
import com.team26.freelance.contracts.dto.ContractDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Map;

@Component
public class ContractSagaConsumer {

    private static final Logger log = LoggerFactory.getLogger(ContractSagaConsumer.class);
    private final ContractService contractService;
    private final ContractSagaPublisher contractSagaPublisher;

    public ContractSagaConsumer(ContractService contractService, ContractSagaPublisher contractSagaPublisher) {
        this.contractService = contractService;
        this.contractSagaPublisher = contractSagaPublisher;
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "contract.saga-listener",
                    arguments = {
                            @org.springframework.amqp.rabbit.annotation.Argument(name = "x-dead-letter-exchange", value = "contract.dlx"),
                            @org.springframework.amqp.rabbit.annotation.Argument(name = "x-dead-letter-routing-key", value = "contract.saga-listener.dlq")
                    }),
            exchange = @Exchange(name = SagaTopics.PROPOSAL_EVENTS_EXCHANGE, type = "topic"),
            key = {SagaTopics.PROPOSAL_ACCEPTED, SagaTopics.PROPOSAL_COMPLETED, SagaTopics.PROPOSAL_CANCELLED}
    ), ackMode = "AUTO")
    public void handleProposalEvents(Map<String, Object> payload,
                                     org.springframework.amqp.support.AmqpHeaders amqpHeaders,
                                     @org.springframework.messaging.handler.annotation.Header(org.springframework.amqp.support.AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) {
        log.info("Received event on {}: {}", routingKey, payload);

        if (SagaTopics.PROPOSAL_ACCEPTED.equals(routingKey)) {
            handleProposalAccepted(payload);
        } else if (SagaTopics.PROPOSAL_COMPLETED.equals(routingKey)) {
            handleProposalCompleted(payload);
        } else if (SagaTopics.PROPOSAL_CANCELLED.equals(routingKey)) {
            handleProposalCancelled(payload);
        }
    }

    private void handleProposalAccepted(Map<String, Object> payload) {
        Long proposalId = extractLong(payload, "proposalId");
        if (proposalId == null) return;

        try {
            contractService.getActiveContractForProposal(proposalId);
            log.info("Active contract already exists for proposalId: {}. Skipping creation.", proposalId);
        } catch (ResponseStatusException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.info("No active contract for proposalId: {}. Creating one.", proposalId);
                Contract contract = new Contract();
                contract.setProposalId(proposalId);
                contract.setJobId(extractLong(payload, "jobId"));
                contract.setFreelancerId(extractLong(payload, "freelancerId"));
                contract.setClientId(extractLong(payload, "clientId"));
                Double amount = extractDouble(payload, "bidAmount");
                if (amount == null) amount = extractDouble(payload, "agreedAmount");
                contract.setAgreedAmount(amount != null ? amount : 0.0);
                contract.setStatus(ContractStatus.ACTIVE);
                contract.setStartDate(LocalDateTime.now());
                Contract saved = contractService.createContract(contract);
                contractSagaPublisher.publishContractCreated(
                        saved.getId(),
                        saved.getProposalId(),
                        saved.getJobId(),
                        saved.getFreelancerId(),
                        saved.getAgreedAmount());
            } else {
                throw e;
            }
        }
    }

    private void handleProposalCompleted(Map<String, Object> payload) {
        Long proposalId = extractLong(payload, "proposalId");
        if (proposalId == null) return;

        try {
            ContractDTO activeContract = contractService.getActiveContractForProposal(proposalId);
            Contract updateDetails = new Contract();
            updateDetails.setStatus(ContractStatus.COMPLETED);
            Contract saved = contractService.update(activeContract.getId(), updateDetails);
            contractSagaPublisher.publishContractStatusChanged(
                    saved.getId(),
                    ContractStatus.ACTIVE,
                    ContractStatus.COMPLETED);
            log.info("Updated contract {} to COMPLETED for proposal {}", activeContract.getId(), proposalId);
        } catch (ResponseStatusException e) {
            log.warn("Received proposal.completed but no active contract found for proposalId: {}", proposalId);
        }
    }

    private void handleProposalCancelled(Map<String, Object> payload) {
        Long proposalId = extractLong(payload, "proposalId");
        if (proposalId == null) return;

        // Try ACTIVE first, then COMPLETED (compensation case: payout failed after completion)
        ContractDTO targetContract = null;
        ContractStatus previousStatus = null;

        try {
            targetContract = contractService.getActiveContractForProposal(proposalId);
            previousStatus = ContractStatus.ACTIVE;
        } catch (ResponseStatusException e) {
            if (e.getStatusCode() != HttpStatus.NOT_FOUND) {
                throw e;
            }
        }

        if (targetContract == null) {
            log.warn("Received proposal.cancelled but no active contract found for proposalId: {}. Nothing to terminate.", proposalId);
            return;
        }

        try {
            Contract updateDetails = new Contract();
            updateDetails.setStatus(ContractStatus.TERMINATED);
            contractService.update(targetContract.getId(), updateDetails);
            contractSagaPublisher.publishContractCancelled(targetContract.getId(), proposalId);
            contractSagaPublisher.publishContractStatusChanged(
                    targetContract.getId(),
                    previousStatus,
                    ContractStatus.TERMINATED);
            log.info("Updated contract {} to TERMINATED and published events for proposal {}", targetContract.getId(), proposalId);
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

    private Long extractLong(Map<String, Object> payload, String key) {
        Object val = payload.get(key);
        if (val instanceof Number n) return n.longValue();
        return null;
    }

    private Double extractDouble(Map<String, Object> payload, String key) {
        Object val = payload.get(key);
        if (val instanceof Number n) return n.doubleValue();
        return null;
    }
}