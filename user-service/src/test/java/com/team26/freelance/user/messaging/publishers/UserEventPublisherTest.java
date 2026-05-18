package com.team26.freelance.user.messaging.publishers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.team26.freelance.contracts.events.SagaTopics;
import com.team26.freelance.contracts.events.UserDeactivatedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitOperations;

class UserEventPublisherTest {

    @Mock
    private RabbitOperations rabbitOperations;

    private UserEventPublisher publisher;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        publisher = new UserEventPublisher(rabbitOperations);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void publishUserDeactivatedUsesUserEventsExchangeAndRoutingKey() {
        UserDeactivatedEvent event = new UserDeactivatedEvent(7L);

        publisher.publishUserDeactivated(event);

        verify(rabbitOperations).convertAndSend(
                eq(SagaTopics.USER_EVENTS_EXCHANGE),
                eq(SagaTopics.USER_DEACTIVATED),
                eq(event),
                org.mockito.ArgumentMatchers.any(MessagePostProcessor.class));
    }

    @Test
    void publishUserDeactivatedPropagatesCorrelationIdHeader() throws Exception {
        MDC.put("correlationId", "acl177-correlation");
        UserDeactivatedEvent event = new UserDeactivatedEvent(7L);

        publisher.publishUserDeactivated(event);

        ArgumentCaptor<MessagePostProcessor> captor = ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(rabbitOperations).convertAndSend(
                eq(SagaTopics.USER_EVENTS_EXCHANGE),
                eq(SagaTopics.USER_DEACTIVATED),
                eq(event),
                captor.capture());

        Message message = new Message(new byte[0], new MessageProperties());
        Message processed = captor.getValue().postProcessMessage(message);
        assertSame(message, processed);
        assertEquals("acl177-correlation", processed.getMessageProperties().getHeaders().get("correlationId"));
    }
}
