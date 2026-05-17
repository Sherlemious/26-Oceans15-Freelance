package com.team26.freelance.job.messaging.consumers;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team26.freelance.job.config.JobEventConfig;
import com.team26.freelance.contracts.events.ProposalAcceptedEvent;
import com.team26.freelance.contracts.events.ProposalCancelledEvent;
import com.team26.freelance.contracts.events.ProposalCompletedEvent;
import com.team26.freelance.contracts.events.ProposalWithdrawnEvent;
import com.team26.freelance.job.service.JobService;

@Component
public class ProposalSagaConsumer {

    private static final Logger log = LoggerFactory.getLogger(ProposalSagaConsumer.class);

    private final JobService jobService;
    private final ObjectMapper objectMapper;

    public ProposalSagaConsumer(JobService jobService, ObjectMapper objectMapper) {
        this.jobService = jobService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = JobEventConfig.JOB_PROPOSAL_SAGA_QUEUE)
    public void onProposalEvent(Message message) throws IOException {
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();
        if (routingKey == null) {
            throw new IllegalArgumentException("Received proposal event without routing key");
        }

        switch (routingKey) {
            case JobEventConfig.PROPOSAL_ACCEPTED_KEY -> {
                ProposalAcceptedEvent event = objectMapper.readValue(message.getBody(), ProposalAcceptedEvent.class);
                log.info("Consumed proposal.accepted for proposalId={} jobId={}", event.proposalId(), event.jobId());
                jobService.handleProposalAccepted(event);
            }
            case JobEventConfig.PROPOSAL_COMPLETED_KEY -> {
                ProposalCompletedEvent event = objectMapper.readValue(message.getBody(), ProposalCompletedEvent.class);
                log.info("Consumed proposal.completed for proposalId={} jobId={}", event.proposalId(), event.jobId());
                jobService.handleProposalCompleted(event);
            }
            case JobEventConfig.PROPOSAL_CANCELLED_KEY -> {
                ProposalCancelledEvent event = objectMapper.readValue(message.getBody(), ProposalCancelledEvent.class);
                log.info("Consumed proposal.cancelled for proposalId={} jobId={}", event.proposalId(), event.jobId());
                jobService.handleProposalCancelled(event);
            }
            case JobEventConfig.PROPOSAL_WITHDRAWN_KEY -> {
                ProposalWithdrawnEvent event = objectMapper.readValue(message.getBody(), ProposalWithdrawnEvent.class);
                log.info("Consumed proposal.withdrawn for proposalId={} jobId={}", event.proposalId(), event.jobId());
                jobService.handleProposalWithdrawn(event);
            }
            default -> throw new IllegalArgumentException("Unsupported proposal routing key: " + routingKey);
        }
    }
}
