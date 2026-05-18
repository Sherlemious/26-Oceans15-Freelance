package com.team26.freelance.job.messaging.consumers;

import java.io.IOException;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team26.freelance.contracts.events.ProposalAcceptedEvent;
import com.team26.freelance.contracts.events.ProposalCancelledEvent;
import com.team26.freelance.contracts.events.ProposalCompletedEvent;
import com.team26.freelance.contracts.events.ProposalWithdrawnEvent;
import com.team26.freelance.job.config.JobEventConfig;
import com.team26.freelance.job.service.JobService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ProposalSagaConsumer {

    private static final Logger log = LoggerFactory.getLogger(ProposalSagaConsumer.class);

    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    private final JobService jobService;
    private final ObjectMapper objectMapper;

    public ProposalSagaConsumer(JobService jobService, ObjectMapper objectMapper) {
        this.jobService = jobService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = JobEventConfig.JOB_PROPOSAL_SAGA_QUEUE)
    public void onProposalEvent(Message message) throws IOException {
        String correlationId = resolveCorrelationId(message);

        MDC.put("correlationId", correlationId);

        String routingKey = message.getMessageProperties().getReceivedRoutingKey();

        if (routingKey != null) {MDC.put("routingKey", routingKey);}

        try {

            if (routingKey == null) {
                throw new IllegalArgumentException(
                        "Received proposal event without routing key"
                );
            }

            switch (routingKey) {

                case JobEventConfig.PROPOSAL_ACCEPTED_KEY -> {

                    ProposalAcceptedEvent event =
                            objectMapper.readValue(
                                    message.getBody(),
                                    ProposalAcceptedEvent.class
                            );

                    MDC.put("proposalId",
                            String.valueOf(event.proposalId()));

                    MDC.put("jobId",
                            String.valueOf(event.jobId()));

                    log.info("Consuming {} for proposalId={}", routingKey, event.proposalId());

                    jobService.handleProposalAccepted(event);

                    log.info("Processed {} for proposalId={}", routingKey, event.proposalId());}

                case JobEventConfig.PROPOSAL_COMPLETED_KEY -> {

                    ProposalCompletedEvent event =
                            objectMapper.readValue(
                                    message.getBody(),
                                    ProposalCompletedEvent.class
                            );

                    MDC.put("proposalId",
                            String.valueOf(event.proposalId()));

                    MDC.put("jobId",
                            String.valueOf(event.jobId()));

                    log.info("Consuming {} for proposalId={}", routingKey, event.proposalId());

                    jobService.handleProposalCompleted(event);

                    log.info("Processed {} for proposalId={}", routingKey, event.proposalId());}

                case JobEventConfig.PROPOSAL_CANCELLED_KEY -> {

                    ProposalCancelledEvent event =
                            objectMapper.readValue(
                                    message.getBody(),
                                    ProposalCancelledEvent.class
                            );

                    MDC.put("proposalId",
                            String.valueOf(event.proposalId()));

                    MDC.put("jobId",
                            String.valueOf(event.jobId()));

                    log.info("Consuming {} for proposalId={}", routingKey, event.proposalId());

                    jobService.handleProposalCancelled(event);

                    log.info("Processed {} for proposalId={}", routingKey, event.proposalId());}

                case JobEventConfig.PROPOSAL_WITHDRAWN_KEY -> {

                    ProposalWithdrawnEvent event =
                            objectMapper.readValue(
                                    message.getBody(),
                                    ProposalWithdrawnEvent.class
                            );

                    MDC.put("proposalId",
                            String.valueOf(event.proposalId()));

                    MDC.put("jobId",
                            String.valueOf(event.jobId()));

                    log.info("Consuming {} for proposalId={}", routingKey, event.proposalId());

                    jobService.handleProposalWithdrawn(event);

                    log.info("Processed {} for proposalId={}", routingKey, event.proposalId());}

                default -> throw new IllegalArgumentException(
                        "Unsupported proposal routing key: " + routingKey
                );
            }

        } catch (Exception ex) {

            log.error(
                    "Failed to process {}: {}",
                    routingKey,
                    ex.getMessage(),
                    ex
            );

            throw ex;
        } finally {

            MDC.remove("correlationId");
            MDC.remove("routingKey");
            MDC.remove("proposalId");
            MDC.remove("jobId");
        }
    }

    private String resolveCorrelationId(Message message) {

        Object value =
                message.getMessageProperties()
                        .getHeaders()
                        .get(CORRELATION_ID_HEADER);

        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        if (value != null) {
            String normalized = String.valueOf(value);
            if (!normalized.isBlank()) {
                return normalized;
            }
        }
        return UUID.randomUUID().toString();
    }
}