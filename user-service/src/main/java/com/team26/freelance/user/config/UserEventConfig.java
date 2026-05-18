package com.team26.freelance.user.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UserEventConfig {

    public static final String USER_EVENTS_EXCHANGE = "user.events";
    public static final String PROPOSAL_EVENTS_EXCHANGE = "proposal.events";
    public static final String USER_DLX_EXCHANGE = "user.dlx";

    public static final String USER_PROPOSAL_SAGA_QUEUE = "user.proposal.saga-listener";
    public static final String USER_PROPOSAL_SAGA_DLQ = "user.proposal.saga-listener.dlq";

    public static final String PROPOSAL_COMPLETED_KEY = "proposal.completed";
    public static final String PROPOSAL_CANCELLED_KEY = "proposal.cancelled";

    @Bean
    public TopicExchange userEventsExchange() {
        return new TopicExchange(USER_EVENTS_EXCHANGE);
    }

    @Bean
    public TopicExchange proposalEventsExchange() {
        return new TopicExchange(PROPOSAL_EVENTS_EXCHANGE);
    }

    @Bean
    public TopicExchange userDeadLetterExchange() {
        return new TopicExchange(USER_DLX_EXCHANGE);
    }

    @Bean
    public MessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public Queue userProposalSagaQueue() {
        return QueueBuilder.durable(USER_PROPOSAL_SAGA_QUEUE)
                .withArgument("x-dead-letter-exchange", USER_DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", USER_PROPOSAL_SAGA_DLQ)
                .build();
    }

    @Bean
    public Queue userProposalSagaDeadLetterQueue() {
        return QueueBuilder.durable(USER_PROPOSAL_SAGA_DLQ).build();
    }

    @Bean
    public Binding proposalCompletedBinding(
            @Qualifier("userProposalSagaQueue") Queue userProposalSagaQueue,
            @Qualifier("proposalEventsExchange") TopicExchange proposalEventsExchange) {
        return BindingBuilder.bind(userProposalSagaQueue)
                .to(proposalEventsExchange)
                .with(PROPOSAL_COMPLETED_KEY);
    }

    @Bean
    public Binding proposalCancelledBinding(
            @Qualifier("userProposalSagaQueue") Queue userProposalSagaQueue,
            @Qualifier("proposalEventsExchange") TopicExchange proposalEventsExchange) {
        return BindingBuilder.bind(userProposalSagaQueue)
                .to(proposalEventsExchange)
                .with(PROPOSAL_CANCELLED_KEY);
    }

    @Bean
    public Binding userProposalSagaDeadLetterBinding(
            @Qualifier("userProposalSagaDeadLetterQueue") Queue userProposalSagaDeadLetterQueue,
            @Qualifier("userDeadLetterExchange") TopicExchange userDeadLetterExchange) {
        return BindingBuilder.bind(userProposalSagaDeadLetterQueue)
                .to(userDeadLetterExchange)
                .with(USER_PROPOSAL_SAGA_DLQ);
    }

    @Bean
    public MessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
