package com.team26.freelance.contract.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ContractEventConfig {

    public static final String EXCHANGE_NAME = "contract.events";
    public static final String PROPOSAL_EXCHANGE_NAME = "proposal.events";
    public static final String USER_EXCHANGE_NAME = "user.events";

    // Exchanges
    @Bean
    public TopicExchange contractEventsExchange() {
        return new TopicExchange(EXCHANGE_NAME);
    }

    @Bean
    public TopicExchange dlxExchange() {
        return new TopicExchange("contract.dlx");
    }

    // Queues
    @Bean
    public Queue sagaListenerQueue() {
        return QueueBuilder.durable("contract.saga-listener")
                .withArgument("x-dead-letter-exchange", "contract.dlx")
                .withArgument("x-dead-letter-routing-key", "contract.saga-listener.dlq")
                .build();
    }

    @Bean
    public Queue sagaListenerDlq() {
        return QueueBuilder.durable("contract.saga-listener.dlq").build();
    }

    @Bean
    public Binding dlqBinding() {
        return BindingBuilder.bind(sagaListenerDlq())
                .to(dlxExchange())
                .with("contract.saga-listener.dlq");
    }
}

