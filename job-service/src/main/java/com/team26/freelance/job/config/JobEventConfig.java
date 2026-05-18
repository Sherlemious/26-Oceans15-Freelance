package com.team26.freelance.job.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableRabbit
public class JobEventConfig {

    public static final String JOB_EVENTS_EXCHANGE = "job.events";
    public static final String PROPOSAL_EVENTS_EXCHANGE = "proposal.events";
    public static final String JOB_DLX_EXCHANGE = "job.dlx";

    public static final String JOB_STATUS_CHANGED_KEY = "job.status-changed";
    public static final String JOB_RATED_KEY = "job.rated";
    public static final String JOB_CLOSED_KEY = "job.closed";

    public static final String PROPOSAL_ACCEPTED_KEY = "proposal.accepted";
    public static final String PROPOSAL_COMPLETED_KEY = "proposal.completed";
    public static final String PROPOSAL_CANCELLED_KEY = "proposal.cancelled";
    public static final String PROPOSAL_WITHDRAWN_KEY = "proposal.withdrawn";

    public static final String JOB_PROPOSAL_SAGA_QUEUE = "job.proposal.saga-listener";
    public static final String JOB_PROPOSAL_SAGA_DLQ = "job.proposal.saga-listener.dlq";

    @Bean
    public TopicExchange jobEventsExchange() {
        return new TopicExchange(JOB_EVENTS_EXCHANGE);
    }

    @Bean
    public TopicExchange proposalEventsExchange() {
        return new TopicExchange(PROPOSAL_EVENTS_EXCHANGE);
    }

    @Bean
    public TopicExchange jobDeadLetterExchange() {
        return new TopicExchange(JOB_DLX_EXCHANGE);
    }

    @Bean
    public Queue jobProposalSagaQueue() {
        return QueueBuilder.durable(JOB_PROPOSAL_SAGA_QUEUE)
                .withArgument("x-dead-letter-exchange", JOB_DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", JOB_PROPOSAL_SAGA_DLQ)
                .build();
    }

    @Bean
    public Queue jobProposalSagaDeadLetterQueue() {
        return QueueBuilder.durable(JOB_PROPOSAL_SAGA_DLQ).build();
    }

    @Bean
    public Binding proposalAcceptedBinding(
            @Qualifier("jobProposalSagaQueue") Queue queue,
            @Qualifier("proposalEventsExchange") TopicExchange exchange) {

        return BindingBuilder.bind(queue)
                .to(exchange)
                .with(PROPOSAL_ACCEPTED_KEY);
    }

    @Bean
    public Binding proposalCompletedBinding(
            @Qualifier("jobProposalSagaQueue") Queue queue,
            @Qualifier("proposalEventsExchange") TopicExchange exchange) {

        return BindingBuilder.bind(queue)
                .to(exchange)
                .with(PROPOSAL_COMPLETED_KEY);
    }

    @Bean
    public Binding proposalCancelledBinding(
            @Qualifier("jobProposalSagaQueue") Queue queue,
            @Qualifier("proposalEventsExchange") TopicExchange exchange) {

        return BindingBuilder.bind(queue)
                .to(exchange)
                .with(PROPOSAL_CANCELLED_KEY);
    }

    @Bean
    public Binding proposalWithdrawnBinding(
            @Qualifier("jobProposalSagaQueue") Queue queue,
            @Qualifier("proposalEventsExchange") TopicExchange exchange) {

        return BindingBuilder.bind(queue)
                .to(exchange)
                .with(PROPOSAL_WITHDRAWN_KEY);
    }

    @Bean
    public Binding jobProposalSagaDeadLetterBinding(
            @Qualifier("jobProposalSagaDeadLetterQueue") Queue dlq,
            @Qualifier("jobDeadLetterExchange") TopicExchange dlx) {

        return BindingBuilder.bind(dlq)
                .to(dlx)
                .with(JOB_PROPOSAL_SAGA_DLQ);
    }

    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter) {

        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter) {

        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setAcknowledgeMode(org.springframework.amqp.core.AcknowledgeMode.AUTO);
        factory.setDefaultRequeueRejected(false);

        return factory;
    }
}