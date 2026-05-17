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
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
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
    bindMessageMdc(message, routingKey);

    log.info("Consuming wallet proposal event routingKey={} correlationId={}", routingKey, MDC.get("correlationId"));

    if (routingKey == null) {
      log.error("Failed wallet proposal event consumption: missing routing key correlationId={}", MDC.get("correlationId"));
      clearMessageMdc();
      throw new IllegalArgumentException("Received proposal event without routing key");
    }

    try {
      switch (routingKey) {
        case SagaTopics.PROPOSAL_COMPLETED -> handleProposalCompleted(message);
        case SagaTopics.PROPOSAL_CANCELLED -> handleProposalCancelled(message);
        default -> throw new IllegalArgumentException("Unsupported proposal routing key: " + routingKey);
      }
      log.info("Wallet proposal event consumed successfully routingKey={} correlationId={}", routingKey, MDC.get("correlationId"));
    } catch (Exception ex) {
      log.error("Wallet proposal event failed routingKey={} correlationId={} proposalId={} contractId={} error={}",
          routingKey,
          MDC.get("correlationId"),
          MDC.get("proposalId"),
          MDC.get("contractId"),
          ex.getMessage(),
          ex);
      throw ex;
    } finally {
      clearMessageMdc();
    }
  }

  private void handleProposalCompleted(Message message) throws IOException {
    ProposalCompletedEvent event = objectMapper.readValue(message.getBody(), ProposalCompletedEvent.class);
    MDC.put("proposalId", String.valueOf(event.proposalId()));
    MDC.put("contractId", String.valueOf(event.contractId()));
    MDC.put("payoutId", "");

    Optional<Payout> payout = payoutService.createPendingPayoutFromProposalCompleted(event);

    if (payout.isEmpty()) {
      log.info("proposal.completed ignored because payout already exists for contractId={} proposalId={}",
          event.contractId(), event.proposalId());
      return;
    }

    Payout created = payout.get();
    MDC.put("payoutId", String.valueOf(created.getId()));
    paymentEventPublisher.publishPaymentInitiated(new PaymentInitiatedEvent(
        created.getId(),
        event.proposalId(),
        event.contractId(),
        BigDecimal.valueOf(created.getAmount())));
    log.info("Handled proposal.completed proposalId={} payoutId={}", event.proposalId(), created.getId());
  }

  private void handleProposalCancelled(Message message) throws IOException {
    ProposalCancelledEvent event = objectMapper.readValue(message.getBody(), ProposalCancelledEvent.class);
    MDC.put("proposalId", String.valueOf(event.proposalId()));
    MDC.put("payoutId", "");

    Optional<Payout> refunded = payoutService.refundPayoutFromProposalCancelled(event);

    if (refunded.isEmpty()) {
      log.info("proposal.cancelled ignored because no payout exists for proposalId={}", event.proposalId());
      return;
    }

    Payout payout = refunded.get();
    MDC.put("contractId", String.valueOf(payout.getContractId()));
    MDC.put("payoutId", String.valueOf(payout.getId()));
    paymentEventPublisher.publishPaymentRefunded(new PaymentRefundedEvent(
        payout.getId(),
        event.proposalId(),
        payout.getContractId(),
        BigDecimal.valueOf(payout.getAmount())));
    log.info("Handled proposal.cancelled proposalId={} payoutId={}", event.proposalId(), payout.getId());
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
    MDC.remove("proposalId");
    MDC.remove("contractId");
    MDC.remove("payoutId");
  }
}
