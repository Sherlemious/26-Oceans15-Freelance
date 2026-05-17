package com.team26.freelance.wallet.messaging;

import com.team26.freelance.contracts.events.PaymentCompletedEvent;
import com.team26.freelance.contracts.events.PaymentFailedEvent;
import com.team26.freelance.contracts.events.PaymentInitiatedEvent;
import com.team26.freelance.contracts.events.PaymentRefundedEvent;
import com.team26.freelance.contracts.events.SagaTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventPublisher.class);
    private static final String CORRELATION_ID_HEADER = "correlationId";

    private final RabbitTemplate rabbitTemplate;

    public PaymentEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishPaymentInitiated(PaymentInitiatedEvent event) {
        publish(SagaTopics.PAYMENT_INITIATED, event, "payment.initiated", event.payoutId(), event.proposalId(), event.contractId());
    }

    public void publishPaymentCompleted(PaymentCompletedEvent event) {
        publish(SagaTopics.PAYMENT_COMPLETED, event, "payment.completed", event.payoutId(), event.proposalId(), event.contractId());
    }

    public void publishPaymentFailed(PaymentFailedEvent event) {
        publish(SagaTopics.PAYMENT_FAILED, event, "payment.failed", event.payoutId(), event.proposalId(), event.contractId());
    }

    public void publishPaymentRefunded(PaymentRefundedEvent event) {
        publish(SagaTopics.PAYMENT_REFUNDED, event, "payment.refunded", event.payoutId(), event.proposalId(), event.contractId());
    }

    private void publish(String routingKey, Object event, String eventName, Long payoutId, Long proposalId, Long contractId) {
        try {
            String correlationId = MDC.get(CORRELATION_ID_HEADER);
            rabbitTemplate.convertAndSend(SagaTopics.PAYMENT_EVENTS_EXCHANGE, routingKey, event, message -> {
                if (correlationId != null && !correlationId.isBlank()) {
                    message.getMessageProperties().setHeader(CORRELATION_ID_HEADER, correlationId);
                }
                return message;
            });
            log.info("Published {} event payoutId={} proposalId={} contractId={} routingKey={}",
                    eventName,
                    payoutId,
                    proposalId,
                    contractId,
                    routingKey);
        } catch (RuntimeException ex) {
            log.error("Failed publishing {} event payoutId={} proposalId={} contractId={} routingKey={}",
                    eventName,
                    payoutId,
                    proposalId,
                    contractId,
                    routingKey,
                    ex);
            throw ex;
        }
    }
}
