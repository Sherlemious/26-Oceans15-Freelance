package com.team26.freelance.wallet.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team26.freelance.contracts.events.PaymentInitiatedEvent;
import com.team26.freelance.contracts.events.PaymentRefundedEvent;
import com.team26.freelance.contracts.events.ProposalCancelledEvent;
import com.team26.freelance.contracts.events.ProposalCompletedEvent;
import com.team26.freelance.contracts.events.SagaTopics;
import com.team26.freelance.wallet.config.PaymentEventConfig;
import com.team26.freelance.wallet.model.Payout;
import com.team26.freelance.wallet.service.PayoutService;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ProposalEventConsumer {

  private static final Logger log = LoggerFactory.getLogger(ProposalEventConsumer.class);

  private final ObjectMapper objectMapper;
  private final PayoutService payoutService;
  private final PaymentEventPublisher paymentEventPublisher;

  public ProposalEventConsumer(ObjectMapper objectMapper,
                               PayoutService payoutService,
                               PaymentEventPublisher paymentEventPublisher) {
    this.objectMapper = objectMapper;
    this.payoutService = payoutService;
    this.paymentEventPublisher = paymentEventPublisher;
  }

  @RabbitListener(queues = PaymentEventConfig.PAYMENT_SAGA_LISTENER_QUEUE)
  public void onProposalEvent(Message message) throws IOException {
    String routingKey = message.getMessageProperties().getReceivedRoutingKey();
    if (routingKey == null) {
      throw new IllegalArgumentException("Received proposal event without routing key");
    }

    switch (routingKey) {
      case SagaTopics.PROPOSAL_COMPLETED -> handleProposalCompleted(message);
      case SagaTopics.PROPOSAL_CANCELLED -> handleProposalCancelled(message);
      default -> throw new IllegalArgumentException("Unsupported proposal routing key: " + routingKey);
    }
  }

  private void handleProposalCompleted(Message message) throws IOException {
    ProposalCompletedEvent event = objectMapper.readValue(message.getBody(), ProposalCompletedEvent.class);
    Optional<Payout> payout = payoutService.createPendingPayoutFromProposalCompleted(event);

    if (payout.isEmpty()) {
      log.info("proposal.completed ignored because payout already exists for contractId={} proposalId={}",
          event.contractId(), event.proposalId());
      return;
    }

    Payout created = payout.get();
    paymentEventPublisher.publishPaymentInitiated(new PaymentInitiatedEvent(
        created.getId(),
        event.proposalId(),
        event.contractId(),
        BigDecimal.valueOf(created.getAmount())));
    log.info("Handled proposal.completed proposalId={} payoutId={}", event.proposalId(), created.getId());
  }

  private void handleProposalCancelled(Message message) throws IOException {
    ProposalCancelledEvent event = objectMapper.readValue(message.getBody(), ProposalCancelledEvent.class);
    Optional<Payout> refunded = payoutService.refundPayoutFromProposalCancelled(event);

    if (refunded.isEmpty()) {
      log.info("proposal.cancelled ignored because no payout exists for proposalId={}", event.proposalId());
      return;
    }

    Payout payout = refunded.get();
    paymentEventPublisher.publishPaymentRefunded(new PaymentRefundedEvent(
        payout.getId(),
        event.proposalId(),
        payout.getContractId(),
        BigDecimal.valueOf(payout.getAmount())));
    log.info("Handled proposal.cancelled proposalId={} payoutId={}", event.proposalId(), payout.getId());
  }
}
