package com.team26.freelance.contract.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ContractSagaConsumer {

    private static final Logger log = LoggerFactory.getLogger(ContractSagaConsumer.class);

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "contract.saga-listener.proposal",
                    arguments = {
                            @org.springframework.amqp.rabbit.annotation.Argument(name = "x-dead-letter-exchange", value = "contract.dlx"),
                            @org.springframework.amqp.rabbit.annotation.Argument(name = "x-dead-letter-routing-key", value = "contract.saga-listener.proposal.dlq")
                    }),
            exchange = @Exchange(name = "proposal.events", type = "topic"),
            key = {"proposal.accepted", "proposal.completed", "proposal.cancelled"}
    ), ackMode="AUTO")
    public void handleProposalEvents(Map<String, Object> payload, org.springframework.amqp.support.AmqpHeaders amqpHeaders, @org.springframework.messaging.handler.annotation.Header(org.springframework.amqp.support.AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) {
        log.info("Received event on {}: {}", routingKey, payload);

        if ("proposal.accepted".equals(routingKey)) {
            // Blocked — requires Task A to merge first
            // calls GET /api/contracts/proposal/{proposalId}/active for pre-check
            // Needs the new endpoint to exist before the saga pre-check call can compile/test
        } else if ("proposal.completed".equals(routingKey)) {
            // Update Contract status to COMPLETED + set endDate -> publish contract.status-changed
            // To be hooked up into service method
        } else if ("proposal.cancelled".equals(routingKey)) {
            // If a Contract exists for this proposal, set status to TERMINATED -> publish contract.cancelled
            // To be hooked up into service method
        }
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "contract.saga-listener.user",
                    arguments = {
                            @org.springframework.amqp.rabbit.annotation.Argument(name = "x-dead-letter-exchange", value = "contract.dlx"),
                            @org.springframework.amqp.rabbit.annotation.Argument(name = "x-dead-letter-routing-key", value = "contract.saga-listener.user.dlq")
                    }),
            exchange = @Exchange(name = "user.events", type = "topic"),
            key = "user.deactivated"
    ), ackMode="AUTO")
    public void handleUserDeactivated(Map<String, Object> payload) {
        log.info("Received user.deactivated event: {}", payload);
        // Optional bookkeeping: log deactivation against ACTIVE contracts
    }
}
