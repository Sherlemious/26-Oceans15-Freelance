package com.team26.freelance.job.messaging.publishers;

import com.team26.freelance.contracts.events.JobClosedEvent;
import com.team26.freelance.contracts.events.JobRatedEvent;
import com.team26.freelance.contracts.events.JobStatusChangedEvent;
import com.team26.freelance.job.config.JobEventConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.springframework.stereotype.Service;

@Service
public class JobSagaPublisher {

    private static final Logger log =
            LoggerFactory.getLogger(JobSagaPublisher.class);

    private static final String CORRELATION_ID_HEADER =
            "X-Correlation-ID";

    private final RabbitOperations rabbitOperations;

    public JobSagaPublisher(RabbitOperations rabbitOperations) {
        this.rabbitOperations = rabbitOperations;
    }

    public void publishJobStatusChanged(JobStatusChangedEvent event) {

        publish(
                JobEventConfig.JOB_STATUS_CHANGED_KEY,
                event,
                "job.status-changed",
                event.jobId()
        );
    }

    public void publishJobRated(JobRatedEvent event) {

        publish(
                JobEventConfig.JOB_RATED_KEY,
                event,
                "job.rated",
                event.jobId()
        );
    }

    public void publishJobClosed(JobClosedEvent event) {

        publish(
                JobEventConfig.JOB_CLOSED_KEY,
                event,
                "job.closed",
                event.jobId()
        );
    }

    private void publish(
            String routingKey,
            Object event,
            String eventName,
            Long jobId
    ) {

        try {

            MDC.put("routingKey", routingKey);
            MDC.put("jobId", String.valueOf(jobId));

            String correlationId = MDC.get("correlationId");

            rabbitOperations.convertAndSend(
                    JobEventConfig.JOB_EVENTS_EXCHANGE,
                    routingKey,
                    event,
                    message -> {

                        if (correlationId != null
                                && !correlationId.isBlank()) {

                            message.getMessageProperties()
                                    .setHeader(
                                            CORRELATION_ID_HEADER,
                                            correlationId
                                    );
                        }
                        return message;
                    }
            );

            log.info(
                    "Published {} for jobId={}",
                    eventName,
                    jobId
            );

        } catch (RuntimeException ex) {
            log.error(
                    "Failed publishing {} for jobId={}",
                    eventName,
                    jobId,
                    ex
            );
            throw ex;

        } finally {
            MDC.remove("routingKey");
            MDC.remove("jobId");
        }
    }
}