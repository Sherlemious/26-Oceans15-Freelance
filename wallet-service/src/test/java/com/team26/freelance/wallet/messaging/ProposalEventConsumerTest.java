package com.team26.freelance.wallet.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team26.freelance.contracts.events.ProposalCancelledEvent;
import com.team26.freelance.contracts.events.ProposalCompletedEvent;
import com.team26.freelance.contracts.events.SagaTopics;
import com.team26.freelance.wallet.model.Payout;
import com.team26.freelance.wallet.model.PayoutMethod;
import com.team26.freelance.wallet.model.PayoutStatus;
import com.team26.freelance.wallet.service.PayoutService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

class ProposalEventConsumerTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private PayoutService payoutService;
  private PaymentEventPublisher paymentEventPublisher;
  private ProposalEventConsumer consumer;

  @BeforeEach
  void setUp() {
    payoutService = org.mockito.Mockito.mock(PayoutService.class);
    paymentEventPublisher = org.mockito.Mockito.mock(PaymentEventPublisher.class);
    consumer = new ProposalEventConsumer(objectMapper, payoutService, paymentEventPublisher);
  }

  @Test
  void proposalCompletedPublishesPaymentInitiatedOnceWhenPayoutCreated() throws Exception {
    ProposalCompletedEvent event = new ProposalCompletedEvent(5L, 7L, 11L, 13L, BigDecimal.valueOf(300.0));
    Payout payout = payout(19L, 13L, 300.0, PayoutStatus.PENDING);
    when(payoutService.createPendingPayoutFromProposalCompleted(any())).thenReturn(Optional.of(payout));

    consumer.onProposalEvent(messageFor(event, SagaTopics.PROPOSAL_COMPLETED));

    verify(payoutService).createPendingPayoutFromProposalCompleted(event);
    verify(paymentEventPublisher).publishPaymentInitiated(any());
  }

  @Test
  void proposalCompletedSkipsPaymentPublishWhenPayoutAlreadyExists() throws Exception {
    ProposalCompletedEvent event = new ProposalCompletedEvent(5L, 7L, 11L, 13L, BigDecimal.valueOf(300.0));
    when(payoutService.createPendingPayoutFromProposalCompleted(any())).thenReturn(Optional.empty());

    consumer.onProposalEvent(messageFor(event, SagaTopics.PROPOSAL_COMPLETED));

    verify(paymentEventPublisher, never()).publishPaymentInitiated(any());
  }

  @Test
  void proposalCancelledPublishesPaymentRefundedWhenRefundProcessed() throws Exception {
    ProposalCancelledEvent event = new ProposalCancelledEvent(5L, 7L, 11L, "client requested");
    Payout refunded = payout(21L, 13L, 120.0, PayoutStatus.REFUNDED);
    when(payoutService.refundPayoutFromProposalCancelled(any())).thenReturn(Optional.of(refunded));

    consumer.onProposalEvent(messageFor(event, SagaTopics.PROPOSAL_CANCELLED));

    verify(payoutService).refundPayoutFromProposalCancelled(event);
    verify(paymentEventPublisher).publishPaymentRefunded(any());
  }

  @Test
  void proposalCancelledSkipsPaymentPublishWhenNoPayoutExists() throws Exception {
    ProposalCancelledEvent event = new ProposalCancelledEvent(5L, 7L, 11L, "client requested");
    when(payoutService.refundPayoutFromProposalCancelled(any())).thenReturn(Optional.empty());

    consumer.onProposalEvent(messageFor(event, SagaTopics.PROPOSAL_CANCELLED));

    verify(paymentEventPublisher, never()).publishPaymentRefunded(any());
  }

  private Message messageFor(Object event, String routingKey) throws Exception {
    MessageProperties properties = new MessageProperties();
    properties.setReceivedRoutingKey(routingKey);
    byte[] body = objectMapper.writeValueAsString(event).getBytes(StandardCharsets.UTF_8);
    return new Message(body, properties);
  }

  private Payout payout(Long id, Long contractId, Double amount, PayoutStatus status) {
    Payout payout = new Payout();
    payout.setId(id);
    payout.setContractId(contractId);
    payout.setFreelancerId(11L);
    payout.setAmount(amount);
    payout.setMethod(PayoutMethod.BANK_TRANSFER);
    payout.setStatus(status);
    return payout;
  }
}
