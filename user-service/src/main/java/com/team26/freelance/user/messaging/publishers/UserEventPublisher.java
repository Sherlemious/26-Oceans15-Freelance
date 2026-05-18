package com.team26.freelance.user.messaging.publishers;

import com.team26.freelance.contracts.events.SagaTopics;
import com.team26.freelance.contracts.events.UserDeactivatedEvent;
import com.team26.freelance.contracts.events.UserRegisteredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.springframework.stereotype.Component;

@Component
public class UserEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(UserEventPublisher.class);
    private static final String CORRELATION_ID_HEADER = "correlationId";

    private final RabbitOperations rabbitOperations;

    public UserEventPublisher(RabbitOperations rabbitOperations) {
        this.rabbitOperations = rabbitOperations;
    }

    public void publishUserRegistered(UserRegisteredEvent event) {
        publish(SagaTopics.USER_REGISTERED, event, "user.registered", event.userId());
    }

    public void publishUserDeactivated(UserDeactivatedEvent event) {
        publish(SagaTopics.USER_DEACTIVATED, event, "user.deactivated", event.userId());
    }

    private void publish(String routingKey, Object event, String eventName, Long userId) {
        try {
            String correlationId = MDC.get(CORRELATION_ID_HEADER);
            rabbitOperations.convertAndSend(SagaTopics.USER_EVENTS_EXCHANGE, routingKey, event, message -> {
                if (correlationId != null && !correlationId.isBlank()) {
                    message.getMessageProperties().setHeader(CORRELATION_ID_HEADER, correlationId);
                }
                return message;
            });
            log.info("Published {} event userId={} routingKey={}", eventName, userId, routingKey);
        } catch (RuntimeException ex) {
            log.error("Failed publishing {} for userId={}", routingKey, userId, ex);
            throw ex;
        }
    }
}
