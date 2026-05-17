package com.team26.freelance.contract.config;

import com.team26.freelance.contracts.events.SagaTopics;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ContractEventConfig {

    public static final String CONTRACT_DLX_EXCHANGE = "contract.dlx";
    public static final String CONTRACT_SAGA_LISTENER_QUEUE = "contract.saga-listener";
    public static final String CONTRACT_SAGA_LISTENER_DLQ = "contract.saga-listener.dlq";

    // Exchanges
    @Bean
    public TopicExchange contractEventsExchange() {
        return new TopicExchange(SagaTopics.CONTRACT_EVENTS_EXCHANGE);
    }

    @Bean
    public TopicExchange proposalEventsExchange() {
        return new TopicExchange(SagaTopics.PROPOSAL_EVENTS_EXCHANGE);
    }

    @Bean
    public TopicExchange userEventsExchange() {
        return new TopicExchange(SagaTopics.USER_EVENTS_EXCHANGE);
    }

    @Bean
    public TopicExchange contractDeadLetterExchange() {
        return new TopicExchange(CONTRACT_DLX_EXCHANGE);
    }

    // Queues
    @Bean
    public Queue contractSagaListenerQueue() {
        return QueueBuilder.durable(CONTRACT_SAGA_LISTENER_QUEUE)
                .withArgument("x-dead-letter-exchange", CONTRACT_DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", CONTRACT_SAGA_LISTENER_DLQ)
                .build();
    }

    @Bean
    public Queue contractSagaListenerDlq() {
        return QueueBuilder.durable(CONTRACT_SAGA_LISTENER_DLQ).build();
    }

    @Bean
    public Binding contractSagaListenerDlqBinding() {
        return BindingBuilder.bind(contractSagaListenerDlq())
                .to(contractDeadLetterExchange())
                .with(CONTRACT_SAGA_LISTENER_DLQ);
    }

    @Bean
    public Binding proposalAcceptedBinding() {
        return BindingBuilder.bind(contractSagaListenerQueue())
                .to(proposalEventsExchange())
                .with(SagaTopics.PROPOSAL_ACCEPTED);
    }

    @Bean
    public Binding proposalCompletedBinding() {
        return BindingBuilder.bind(contractSagaListenerQueue())
                .to(proposalEventsExchange())
                .with(SagaTopics.PROPOSAL_COMPLETED);
    }

    @Bean
    public Binding proposalCancelledBinding() {
        return BindingBuilder.bind(contractSagaListenerQueue())
                .to(proposalEventsExchange())
                .with(SagaTopics.PROPOSAL_CANCELLED);
    }

    @Bean
    public Binding userDeactivatedBinding() {
        return BindingBuilder.bind(contractSagaListenerQueue())
                .to(userEventsExchange())
                .with(SagaTopics.USER_DEACTIVATED);
    }
}
