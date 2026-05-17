package com.team26.freelance.job.messaging.publishers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import com.team26.freelance.job.config.JobEventConfig;
import com.team26.freelance.job.events.JobClosedEvent;
import com.team26.freelance.job.events.JobRatedEvent;
import com.team26.freelance.job.events.JobStatusChangedEvent;

@Service
public class JobSagaPublisher {

    private static final Logger log = LoggerFactory.getLogger(JobSagaPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public JobSagaPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
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
        rabbitTemplate.convertAndSend(JobEventConfig.JOB_EVENTS_EXCHANGE, routingKey, event);
        log.info("Published {} event for jobId={} with routingKey={}", eventName, jobId, routingKey);
    }
}
