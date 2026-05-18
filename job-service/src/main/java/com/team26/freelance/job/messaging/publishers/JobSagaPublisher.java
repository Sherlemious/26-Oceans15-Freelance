package com.team26.freelance.job.messaging.publishers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.springframework.stereotype.Service;

import com.team26.freelance.job.config.JobEventConfig;
import com.team26.freelance.contracts.events.JobClosedEvent;
import com.team26.freelance.contracts.events.JobRatedEvent;
import com.team26.freelance.contracts.events.JobStatusChangedEvent;

@Service
public class JobSagaPublisher {

    private static final Logger log = LoggerFactory.getLogger(JobSagaPublisher.class);
    private static final String CORRELATION_ID_HEADER = "correlationId";

    private final RabbitOperations rabbitOperations;

    public JobSagaPublisher(RabbitOperations rabbitOperations) {
        this.rabbitOperations = rabbitOperations;
    }

    public void publishJobStatusChanged(JobStatusChangedEvent event) {
        publish(JobEventConfig.JOB_STATUS_CHANGED_KEY, event, "job status changed", event.jobId());
    }

    public void publishJobRated(JobRatedEvent event) {
        publish(JobEventConfig.JOB_RATED_KEY, event, "job rated", event.jobId());
    }

    public void publishJobClosed(JobClosedEvent event) {
        publish(JobEventConfig.JOB_CLOSED_KEY, event, "job closed", event.jobId());
    }

    private void publish(String routingKey, Object event, String eventName, Long jobId) {
        try {
            String correlationId = MDC.get(CORRELATION_ID_HEADER);
            rabbitOperations.convertAndSend(JobEventConfig.JOB_EVENTS_EXCHANGE, routingKey, event, message -> {
                if (correlationId != null && !correlationId.isBlank()) {
                    message.getMessageProperties().setHeader(CORRELATION_ID_HEADER, correlationId);
                }
                return message;
            });
            log.info("Published {} event for jobId={} with routingKey={}", eventName, jobId, routingKey);
        } catch (RuntimeException ex) {
            log.error("Failed publishing {} event for jobId={} routingKey={}", eventName, jobId, routingKey, ex);
            throw ex;
        }
    }
}
