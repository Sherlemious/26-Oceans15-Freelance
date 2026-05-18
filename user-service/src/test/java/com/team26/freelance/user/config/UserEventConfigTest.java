package com.team26.freelance.user.config;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class UserEventConfigTest {

    @Test
    void shouldDeclareRequiredRabbitMqTopologyBeans() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(UserEventConfig.class)) {
            TopicExchange userEventsExchange = context.getBean("userEventsExchange", TopicExchange.class);
            assertEquals(UserEventConfig.USER_EVENTS_EXCHANGE, userEventsExchange.getName());

            TopicExchange proposalEventsExchange = context.getBean("proposalEventsExchange", TopicExchange.class);
            assertEquals(UserEventConfig.PROPOSAL_EVENTS_EXCHANGE, proposalEventsExchange.getName());

            Queue sagaQueue = context.getBean("userProposalSagaQueue", Queue.class);
            assertEquals(UserEventConfig.USER_PROPOSAL_SAGA_QUEUE, sagaQueue.getName());

            Queue dlq = context.getBean("userProposalSagaDeadLetterQueue", Queue.class);
            assertEquals(UserEventConfig.USER_PROPOSAL_SAGA_DLQ, dlq.getName());

            Map<String, Object> arguments = sagaQueue.getArguments();
            assertNotNull(arguments);
            assertEquals(UserEventConfig.USER_DLX_EXCHANGE, arguments.get("x-dead-letter-exchange"));
            assertEquals(UserEventConfig.USER_PROPOSAL_SAGA_DLQ, arguments.get("x-dead-letter-routing-key"));

            Binding proposalCompletedBinding = context.getBean("proposalCompletedBinding", Binding.class);
            assertEquals(UserEventConfig.PROPOSAL_COMPLETED_KEY, proposalCompletedBinding.getRoutingKey());
            assertEquals(UserEventConfig.PROPOSAL_EVENTS_EXCHANGE, proposalCompletedBinding.getExchange());
            assertEquals(UserEventConfig.USER_PROPOSAL_SAGA_QUEUE, proposalCompletedBinding.getDestination());

            Binding proposalCancelledBinding = context.getBean("proposalCancelledBinding", Binding.class);
            assertEquals(UserEventConfig.PROPOSAL_CANCELLED_KEY, proposalCancelledBinding.getRoutingKey());
            assertEquals(UserEventConfig.PROPOSAL_EVENTS_EXCHANGE, proposalCancelledBinding.getExchange());
            assertEquals(UserEventConfig.USER_PROPOSAL_SAGA_QUEUE, proposalCancelledBinding.getDestination());
        }
    }
}
