package com.team26.freelance.proposal.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team26.freelance.contracts.events.SagaTopics;
import com.team26.freelance.contracts.events.UserDeactivatedEvent;
import com.team26.freelance.proposal.model.Proposal;
import com.team26.freelance.proposal.model.ProposalStatus;
import com.team26.freelance.proposal.repository.ProposalRepository;
import com.team26.freelance.proposal.service.ProposalCacheEvictionService;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Configuration
@EnableRabbit
public class ProposalEventConfig {

    public static final String PROPOSAL_DLX_EXCHANGE = "proposal.dlx";
    public static final String PROPOSAL_SAGA_FEEDBACK_QUEUE = "proposal.saga-feedback";
    public static final String PROPOSAL_SAGA_FEEDBACK_DLQ = "proposal.saga-feedback.dlq";
    public static final String PROPOSAL_USER_SAGA_QUEUE = "proposal.user.saga-listener";
    public static final String PROPOSAL_USER_SAGA_DLQ = "proposal.user.saga-listener.dlq";

    @Bean
    public MessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }

    @Bean
    public TopicExchange proposalEventsExchange() {
        return new TopicExchange(SagaTopics.PROPOSAL_EVENTS_EXCHANGE);
    }

    @Bean
    public TopicExchange contractEventsExchange() {
        return new TopicExchange(SagaTopics.CONTRACT_EVENTS_EXCHANGE);
    }

    @Bean
    public TopicExchange userEventsExchange() {
        return new TopicExchange(SagaTopics.USER_EVENTS_EXCHANGE);
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
    public Queue proposalUserSagaQueue() {
        return QueueBuilder.durable(PROPOSAL_USER_SAGA_QUEUE)
                .withArgument("x-dead-letter-exchange", PROPOSAL_DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", PROPOSAL_USER_SAGA_DLQ)
                .build();
    }

    @Bean
    public Queue proposalUserSagaDlq() {
        return QueueBuilder.durable(PROPOSAL_USER_SAGA_DLQ).build();
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
        public Binding proposalUserSagaDlqBinding(
            @Qualifier("proposalUserSagaDlq") Queue proposalUserSagaDlq,
            @Qualifier("proposalDeadLetterExchange") TopicExchange proposalDeadLetterExchange) {
        return BindingBuilder.bind(proposalUserSagaDlq)
            .to(proposalDeadLetterExchange)
            .with(PROPOSAL_USER_SAGA_DLQ);
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
        public Binding userDeactivatedBinding(
            @Qualifier("proposalUserSagaQueue") Queue proposalUserSagaQueue,
            @Qualifier("userEventsExchange") TopicExchange userEventsExchange) {
        return BindingBuilder.bind(proposalUserSagaQueue)
            .to(userEventsExchange)
            .with(SagaTopics.USER_DEACTIVATED);
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

@Component
class UserDeactivatedConsumer {

    private static final Logger log = LoggerFactory.getLogger(UserDeactivatedConsumer.class);

    private final ObjectMapper objectMapper;
    private final ProposalRepository proposalRepository;
    private final ProposalCacheEvictionService cacheEvictionService;

    UserDeactivatedConsumer(ObjectMapper objectMapper,
                            ProposalRepository proposalRepository,
                            ProposalCacheEvictionService cacheEvictionService) {
        this.objectMapper = objectMapper;
        this.proposalRepository = proposalRepository;
        this.cacheEvictionService = cacheEvictionService;
    }

    @RabbitListener(queues = ProposalEventConfig.PROPOSAL_USER_SAGA_QUEUE)
    @Transactional
    public void onUserDeactivated(Message message) throws IOException {
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();
        bindMessageMdc(message, routingKey);

        log.info("Consuming user event routingKey={} correlationId={}", routingKey, MDC.get("correlationId"));

        if (!SagaTopics.USER_DEACTIVATED.equals(routingKey)) {
            log.error("Unsupported user event routingKey={}", routingKey);
            throw new IllegalArgumentException("Unsupported user event routing key: " + routingKey);
        }

        try {
            UserDeactivatedEvent event = objectMapper.readValue(message.getBody(), UserDeactivatedEvent.class);
            if (event == null || event.userId() == null) {
                throw new IllegalArgumentException("user.deactivated event missing userId");
            }

            MDC.put("userId", String.valueOf(event.userId()));

            List<Proposal> submitted = proposalRepository
                    .findByStatusOrderBySubmittedAtDesc(ProposalStatus.SUBMITTED);
            List<Proposal> toWithdraw = submitted.stream()
                    .filter(proposal -> event.userId().equals(proposal.getFreelancerId()))
                    .toList();

            if (toWithdraw.isEmpty()) {
                log.info("No SUBMITTED proposals to withdraw for userId={}", event.userId());
                return;
            }

            toWithdraw.forEach(proposal -> proposal.setStatus(ProposalStatus.WITHDRAWN));
            proposalRepository.saveAll(toWithdraw);
            toWithdraw.forEach(proposal -> cacheEvictionService.evictProposalCaches(proposal.getId()));

            log.info("Withdrawn {} SUBMITTED proposals for userId={}", toWithdraw.size(), event.userId());
        } catch (Exception ex) {
            log.error("Failed user.deactivated processing routingKey={} userId={} error={}",
                    routingKey,
                    MDC.get("userId"),
                    ex.getMessage(),
                    ex);
            throw ex;
        } finally {
            clearMessageMdc();
        }
    }

    private void bindMessageMdc(Message message, String routingKey) {
        MessageProperties properties = message.getMessageProperties();
        Object correlationIdHeader = properties.getHeaders().get("correlationId");

        if (correlationIdHeader != null) {
            MDC.put("correlationId", correlationIdHeader.toString());
        }

        if (routingKey != null) {
            MDC.put("routingKey", routingKey);
        }
    }

    private void clearMessageMdc() {
        MDC.remove("correlationId");
        MDC.remove("routingKey");
        MDC.remove("userId");
    }
}
