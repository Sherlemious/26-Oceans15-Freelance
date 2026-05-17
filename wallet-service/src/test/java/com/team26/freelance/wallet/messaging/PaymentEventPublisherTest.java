package com.team26.freelance.wallet.messaging;

import com.team26.freelance.contracts.events.PaymentCompletedEvent;
import com.team26.freelance.contracts.events.PaymentFailedEvent;
import com.team26.freelance.contracts.events.PaymentInitiatedEvent;
import com.team26.freelance.contracts.events.PaymentRefundedEvent;
import com.team26.freelance.contracts.events.SagaTopics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.slf4j.MDC;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PaymentEventPublisherTest {

    private RabbitOperations rabbitOperations;
    private PaymentEventPublisher publisher;

    @BeforeEach
    void setUp() {
        rabbitOperations = mock(RabbitOperations.class);
        publisher = new PaymentEventPublisher(rabbitOperations);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void shouldPublishPaymentInitiatedToPaymentExchangeWithRoutingKey() {
        PaymentInitiatedEvent event = new PaymentInitiatedEvent(1L, 2L, 3L, BigDecimal.TEN);

        publisher.publishPaymentInitiated(event);

        verify(rabbitOperations).convertAndSend(
                eq(SagaTopics.PAYMENT_EVENTS_EXCHANGE),
                eq(SagaTopics.PAYMENT_INITIATED),
                eq(event),
                any(MessagePostProcessor.class));
    }

    @Test
    void shouldPublishPaymentCompletedToPaymentExchangeWithRoutingKey() {
        PaymentCompletedEvent event = new PaymentCompletedEvent(11L, 22L, 33L, BigDecimal.ONE);

        publisher.publishPaymentCompleted(event);

        verify(rabbitOperations).convertAndSend(
                eq(SagaTopics.PAYMENT_EVENTS_EXCHANGE),
                eq(SagaTopics.PAYMENT_COMPLETED),
                eq(event),
                any(MessagePostProcessor.class));
    }

    @Test
    void shouldPublishPaymentFailedToPaymentExchangeWithRoutingKey() {
        PaymentFailedEvent event = new PaymentFailedEvent(5L, 6L, 7L, "gateway timeout");

        publisher.publishPaymentFailed(event);

        verify(rabbitOperations).convertAndSend(
                eq(SagaTopics.PAYMENT_EVENTS_EXCHANGE),
                eq(SagaTopics.PAYMENT_FAILED),
                eq(event),
                any(MessagePostProcessor.class));
    }

    @Test
    void shouldPublishPaymentRefundedToPaymentExchangeWithRoutingKey() {
        PaymentRefundedEvent event = new PaymentRefundedEvent(101L, 202L, 303L, BigDecimal.valueOf(3.50));

        publisher.publishPaymentRefunded(event);

        verify(rabbitOperations).convertAndSend(
                eq(SagaTopics.PAYMENT_EVENTS_EXCHANGE),
                eq(SagaTopics.PAYMENT_REFUNDED),
                eq(event),
                any(MessagePostProcessor.class));
    }

    @Test
    void shouldAttachCorrelationIdHeaderWhenAvailable() {
        MDC.put("correlationId", "corr-123");
        PaymentInitiatedEvent event = new PaymentInitiatedEvent(1L, 2L, 3L, BigDecimal.TEN);

        publisher.publishPaymentInitiated(event);

        ArgumentCaptor<MessagePostProcessor> postProcessorCaptor = ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(rabbitOperations).convertAndSend(
                eq(SagaTopics.PAYMENT_EVENTS_EXCHANGE),
                eq(SagaTopics.PAYMENT_INITIATED),
                eq(event),
                postProcessorCaptor.capture());

        Message processedMessage = postProcessorCaptor.getValue().postProcessMessage(new Message(new byte[0]));
        assertEquals("corr-123", processedMessage.getMessageProperties().getHeaders().get("correlationId"));
    }

    @Test
    void shouldSkipCorrelationIdHeaderWhenNotAvailable() {
        PaymentInitiatedEvent event = new PaymentInitiatedEvent(1L, 2L, 3L, BigDecimal.TEN);

        publisher.publishPaymentInitiated(event);

        ArgumentCaptor<MessagePostProcessor> postProcessorCaptor = ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(rabbitOperations).convertAndSend(
                eq(SagaTopics.PAYMENT_EVENTS_EXCHANGE),
                eq(SagaTopics.PAYMENT_INITIATED),
                eq(event),
                postProcessorCaptor.capture());

        Message processedMessage = postProcessorCaptor.getValue().postProcessMessage(new Message(new byte[0]));
        assertNull(processedMessage.getMessageProperties().getHeaders().get("correlationId"));
    }
}
