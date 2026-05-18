package com.team26.freelance.wallet.config;

import com.team26.freelance.contracts.events.SagaTopics;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentEventConfig {

    public static final String PAYMENT_DLX_EXCHANGE = "payment.dlx";
    public static final String PAYMENT_SAGA_LISTENER_QUEUE = "payment.saga-listener";
    public static final String PAYMENT_SAGA_LISTENER_DLQ = "payment.saga-listener.dlq";

    @Bean
    public TopicExchange paymentEventsExchange() {
        return new TopicExchange(SagaTopics.PAYMENT_EVENTS_EXCHANGE);
    }

    @Bean
    public TopicExchange proposalEventsExchange() {
        return new TopicExchange(SagaTopics.PROPOSAL_EVENTS_EXCHANGE);
    }

    @Bean
    public TopicExchange paymentDeadLetterExchange() {
        return new TopicExchange(PAYMENT_DLX_EXCHANGE);
    }

    @Bean
    public MessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public Queue paymentSagaListenerQueue() {
        return QueueBuilder.durable(PAYMENT_SAGA_LISTENER_QUEUE)
                .withArgument("x-dead-letter-exchange", PAYMENT_DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", PAYMENT_SAGA_LISTENER_DLQ)
                .build();
    }

    @Bean
    public Queue paymentSagaListenerDeadLetterQueue() {
        return QueueBuilder.durable(PAYMENT_SAGA_LISTENER_DLQ).build();
    }

    @Bean
    public Binding proposalCompletedBinding(Queue paymentSagaListenerQueue, TopicExchange proposalEventsExchange) {
        return BindingBuilder.bind(paymentSagaListenerQueue)
                .to(proposalEventsExchange)
                .with(SagaTopics.PROPOSAL_COMPLETED);
    }

    @Bean
    public Binding proposalCancelledBinding(Queue paymentSagaListenerQueue, TopicExchange proposalEventsExchange) {
        return BindingBuilder.bind(paymentSagaListenerQueue)
                .to(proposalEventsExchange)
                .with(SagaTopics.PROPOSAL_CANCELLED);
    }

    @Bean
    public Binding paymentSagaListenerDeadLetterBinding(
            Queue paymentSagaListenerDeadLetterQueue,
            TopicExchange paymentDeadLetterExchange) {
        return BindingBuilder.bind(paymentSagaListenerDeadLetterQueue)
                .to(paymentDeadLetterExchange)
                .with(PAYMENT_SAGA_LISTENER_DLQ);
    }
}
