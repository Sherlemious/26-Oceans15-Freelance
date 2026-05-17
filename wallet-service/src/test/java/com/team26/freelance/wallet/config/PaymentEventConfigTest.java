package com.team26.freelance.wallet.config;

import com.team26.freelance.contracts.events.SagaTopics;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PaymentEventConfigTest {

    private final PaymentEventConfig config = new PaymentEventConfig();

    @Test
    void shouldDeclareExpectedExchanges() {
        TopicExchange paymentEvents = config.paymentEventsExchange();
        TopicExchange proposalEvents = config.proposalEventsExchange();
        TopicExchange paymentDlx = config.paymentDeadLetterExchange();

        assertEquals(SagaTopics.PAYMENT_EVENTS_EXCHANGE, paymentEvents.getName());
        assertEquals(SagaTopics.PROPOSAL_EVENTS_EXCHANGE, proposalEvents.getName());
        assertEquals(PaymentEventConfig.PAYMENT_DLX_EXCHANGE, paymentDlx.getName());
    }

    @Test
    void shouldDeclareSagaListenerQueueWithDeadLetterArguments() {
        Queue sagaListenerQueue = config.paymentSagaListenerQueue();

        assertEquals(PaymentEventConfig.PAYMENT_SAGA_LISTENER_QUEUE, sagaListenerQueue.getName());
        assertEquals(PaymentEventConfig.PAYMENT_DLX_EXCHANGE,
                sagaListenerQueue.getArguments().get("x-dead-letter-exchange"));
        assertEquals(PaymentEventConfig.PAYMENT_SAGA_LISTENER_DLQ,
                sagaListenerQueue.getArguments().get("x-dead-letter-routing-key"));
    }

    @Test
    void shouldDeclareExpectedBindings() {
        Queue queue = config.paymentSagaListenerQueue();
        TopicExchange proposalExchange = config.proposalEventsExchange();
        Queue dlq = config.paymentSagaListenerDeadLetterQueue();
        TopicExchange dlx = config.paymentDeadLetterExchange();

        Binding completedBinding = config.proposalCompletedBinding(queue, proposalExchange);
        Binding cancelledBinding = config.proposalCancelledBinding(queue, proposalExchange);
        Binding dlqBinding = config.paymentSagaListenerDeadLetterBinding(dlq, dlx);

        assertEquals(SagaTopics.PROPOSAL_COMPLETED, completedBinding.getRoutingKey());
        assertEquals(SagaTopics.PROPOSAL_CANCELLED, cancelledBinding.getRoutingKey());
        assertEquals(PaymentEventConfig.PAYMENT_SAGA_LISTENER_DLQ, dlqBinding.getRoutingKey());

        assertEquals(PaymentEventConfig.PAYMENT_SAGA_LISTENER_QUEUE, completedBinding.getDestination());
        assertEquals(PaymentEventConfig.PAYMENT_SAGA_LISTENER_QUEUE, cancelledBinding.getDestination());
        assertEquals(PaymentEventConfig.PAYMENT_SAGA_LISTENER_DLQ, dlqBinding.getDestination());

        assertEquals(SagaTopics.PROPOSAL_EVENTS_EXCHANGE, completedBinding.getExchange());
        assertEquals(SagaTopics.PROPOSAL_EVENTS_EXCHANGE, cancelledBinding.getExchange());
        assertEquals(PaymentEventConfig.PAYMENT_DLX_EXCHANGE, dlqBinding.getExchange());
    }
}
