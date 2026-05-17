package com.team26.freelance.contract.config;

import com.team26.freelance.contracts.events.SagaTopics;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ContractEventConfig {

    // Exchanges
    @Bean
    public TopicExchange contractEventsExchange() {
        return new TopicExchange(SagaTopics.CONTRACT_EVENTS_EXCHANGE);
    }

    @Bean
    public TopicExchange dlxExchange() {
        return new TopicExchange("contract.dlx");
    }

    // Queues
    @Bean
    public Queue sagaListenerProposalQueue() {
        return QueueBuilder.durable("contract.saga-listener.proposal")
                .withArgument("x-dead-letter-exchange", "contract.dlx")
                .withArgument("x-dead-letter-routing-key", "contract.saga-listener.proposal.dlq")
                .build();
    }

    @Bean
    public Queue sagaListenerProposalDlq() {
        return QueueBuilder.durable("contract.saga-listener.proposal.dlq").build();
    }

    @Bean
    public Binding dlqProposalBinding() {
        return BindingBuilder.bind(sagaListenerProposalDlq())
                .to(dlxExchange())
                .with("contract.saga-listener.proposal.dlq");
    }

    @Bean
    public Queue sagaListenerUserQueue() {
        return QueueBuilder.durable("contract.saga-listener.user")
                .withArgument("x-dead-letter-exchange", "contract.dlx")
                .withArgument("x-dead-letter-routing-key", "contract.saga-listener.user.dlq")
                .build();
    }

    @Bean
    public Queue sagaListenerUserDlq() {
        return QueueBuilder.durable("contract.saga-listener.user.dlq").build();
    }

    @Bean
    public Binding dlqUserBinding() {
        return BindingBuilder.bind(sagaListenerUserDlq())
                .to(dlxExchange())
                .with("contract.saga-listener.user.dlq");
    }
}
