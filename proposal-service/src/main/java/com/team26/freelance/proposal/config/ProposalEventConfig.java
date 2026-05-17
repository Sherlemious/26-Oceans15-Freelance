package com.team26.freelance.proposal.config;

import com.team26.freelance.contracts.events.SagaTopics;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableRabbit
public class ProposalEventConfig {

    public static final String PROPOSAL_DLX_EXCHANGE = "proposal.dlx";
    public static final String PROPOSAL_SAGA_FEEDBACK_QUEUE = "proposal.saga-feedback";
    public static final String PROPOSAL_SAGA_FEEDBACK_DLQ = "proposal.saga-feedback.dlq";

    @Bean
    public TopicExchange proposalEventsExchange() {
        return new TopicExchange(SagaTopics.PROPOSAL_EVENTS_EXCHANGE);
    }

    @Bean
    public TopicExchange contractEventsExchange() {
        return new TopicExchange(SagaTopics.CONTRACT_EVENTS_EXCHANGE);
    }

    @Bean
    public TopicExchange paymentEventsExchange() {
        return new TopicExchange(SagaTopics.PAYMENT_EVENTS_EXCHANGE);
    }

    @Bean
    public TopicExchange proposalDeadLetterExchange() {
        return new TopicExchange(PROPOSAL_DLX_EXCHANGE);
    }

    @Bean
    public Queue proposalSagaFeedbackQueue() {
        return QueueBuilder.durable(PROPOSAL_SAGA_FEEDBACK_QUEUE)
                .withArgument("x-dead-letter-exchange", PROPOSAL_DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", PROPOSAL_SAGA_FEEDBACK_DLQ)
                .build();
    }

    @Bean
    public Queue proposalSagaFeedbackDlq() {
        return QueueBuilder.durable(PROPOSAL_SAGA_FEEDBACK_DLQ).build();
    }

    @Bean
    public Binding proposalSagaFeedbackDlqBinding(
            @Qualifier("proposalSagaFeedbackDlq") Queue proposalSagaFeedbackDlq,
            @Qualifier("proposalDeadLetterExchange") TopicExchange proposalDeadLetterExchange) {
        return BindingBuilder.bind(proposalSagaFeedbackDlq)
                .to(proposalDeadLetterExchange)
                .with(PROPOSAL_SAGA_FEEDBACK_DLQ);
    }

    @Bean
    public Binding contractCreatedBinding(
            @Qualifier("proposalSagaFeedbackQueue") Queue proposalSagaFeedbackQueue,
            @Qualifier("contractEventsExchange") TopicExchange contractEventsExchange) {
        return BindingBuilder.bind(proposalSagaFeedbackQueue)
                .to(contractEventsExchange)
                .with(SagaTopics.CONTRACT_CREATED);
    }

    @Bean
    public Binding contractStatusChangedBinding(
            @Qualifier("proposalSagaFeedbackQueue") Queue proposalSagaFeedbackQueue,
            @Qualifier("contractEventsExchange") TopicExchange contractEventsExchange) {
        return BindingBuilder.bind(proposalSagaFeedbackQueue)
                .to(contractEventsExchange)
                .with(SagaTopics.CONTRACT_STATUS_CHANGED);
    }

    @Bean
    public Binding paymentInitiatedBinding(
            @Qualifier("proposalSagaFeedbackQueue") Queue proposalSagaFeedbackQueue,
            @Qualifier("paymentEventsExchange") TopicExchange paymentEventsExchange) {
        return BindingBuilder.bind(proposalSagaFeedbackQueue)
                .to(paymentEventsExchange)
                .with(SagaTopics.PAYMENT_INITIATED);
    }

    @Bean
    public Binding paymentCompletedBinding(
            @Qualifier("proposalSagaFeedbackQueue") Queue proposalSagaFeedbackQueue,
            @Qualifier("paymentEventsExchange") TopicExchange paymentEventsExchange) {
        return BindingBuilder.bind(proposalSagaFeedbackQueue)
                .to(paymentEventsExchange)
                .with(SagaTopics.PAYMENT_COMPLETED);
    }

    @Bean
    public Binding paymentFailedBinding(
            @Qualifier("proposalSagaFeedbackQueue") Queue proposalSagaFeedbackQueue,
            @Qualifier("paymentEventsExchange") TopicExchange paymentEventsExchange) {
        return BindingBuilder.bind(proposalSagaFeedbackQueue)
                .to(paymentEventsExchange)
                .with(SagaTopics.PAYMENT_FAILED);
    }

    @Bean
    public Binding paymentRefundedBinding(
            @Qualifier("proposalSagaFeedbackQueue") Queue proposalSagaFeedbackQueue,
            @Qualifier("paymentEventsExchange") TopicExchange paymentEventsExchange) {
        return BindingBuilder.bind(proposalSagaFeedbackQueue)
                .to(paymentEventsExchange)
                .with(SagaTopics.PAYMENT_REFUNDED);
    }
}
