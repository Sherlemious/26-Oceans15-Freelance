package com.team26.freelance.contract.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team26.freelance.contract.model.Contract;
import com.team26.freelance.contract.model.ContractStatus;
import com.team26.freelance.contract.service.ContractService;
import com.team26.freelance.contracts.dto.ContractDTO;
import com.team26.freelance.contracts.events.ProposalAcceptedEvent;
import com.team26.freelance.contracts.events.ProposalCancelledEvent;
import com.team26.freelance.contracts.events.ProposalCompletedEvent;
import com.team26.freelance.contracts.events.SagaTopics;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContractSagaConsumerTest {

    @Mock
    private ContractService contractService;

    @Mock
    private com.team26.freelance.contract.messaging.publishers.ContractSagaPublisher contractSagaPublisher;

    private com.team26.freelance.contract.messaging.consumers.ContractSagaConsumer consumer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        consumer = new com.team26.freelance.contract.messaging.consumers.ContractSagaConsumer(contractService, contractSagaPublisher, objectMapper);
    }

    @Test
    void handleProposalAcceptedCreatesContractWhenNoneExists() throws Exception {
        when(contractService.getActiveContractForProposal(10L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        when(contractService.createContract(any())).thenAnswer(invocation -> {
            Contract saved = invocation.getArgument(0);
            saved.setId(100L);
            return saved;
        });

        consumer.handleProposalEvents(messageFor(
                new ProposalAcceptedEvent(10L, 20L, 40L, 30L, BigDecimal.valueOf(500.0)),
                SagaTopics.PROPOSAL_ACCEPTED));

        ArgumentCaptor<Contract> contractCaptor = ArgumentCaptor.forClass(Contract.class);
        verify(contractService).createContract(contractCaptor.capture());
        
        Contract created = contractCaptor.getValue();
        assertEquals(10L, created.getProposalId());
        assertEquals(20L, created.getJobId());
        assertEquals(30L, created.getFreelancerId());
        assertEquals(40L, created.getClientId());
        assertEquals(500.0, created.getAgreedAmount());
        assertEquals(ContractStatus.ACTIVE, created.getStatus());
    }

    @Test
    void handleProposalAcceptedSkipsCreationWhenActiveContractExists() throws Exception {
        when(contractService.getActiveContractForProposal(10L)).thenReturn(new ContractDTO());

        consumer.handleProposalEvents(messageFor(
                new ProposalAcceptedEvent(10L, 20L, 40L, 30L, BigDecimal.valueOf(500.0)),
                SagaTopics.PROPOSAL_ACCEPTED));

        verify(contractService, never()).createContract(any());
    }

    @Test
    void handleProposalCompletedUpdatesContractStatus() throws Exception {
        ContractDTO activeContract = new ContractDTO();
        activeContract.setId(100L);
        when(contractService.getActiveContractForProposal(10L)).thenReturn(activeContract);

        consumer.handleProposalEvents(messageFor(
                new ProposalCompletedEvent(10L, 20L, 30L, 100L, BigDecimal.valueOf(500.0)),
                SagaTopics.PROPOSAL_COMPLETED));

        ArgumentCaptor<Contract> contractCaptor = ArgumentCaptor.forClass(Contract.class);
        verify(contractService).update(eq(100L), contractCaptor.capture());
        assertEquals(ContractStatus.COMPLETED, contractCaptor.getValue().getStatus());
    }

    @Test
    void handleProposalCancelledUpdatesStatusAndPublishesEvent() throws Exception {
        ContractDTO activeContract = new ContractDTO();
        activeContract.setId(100L);
        when(contractService.getActiveContractForProposal(10L)).thenReturn(activeContract);

        consumer.handleProposalEvents(messageFor(
                new ProposalCancelledEvent(10L, 20L, 30L, "payment failed"),
                SagaTopics.PROPOSAL_CANCELLED));

        ArgumentCaptor<Contract> contractCaptor = ArgumentCaptor.forClass(Contract.class);
        verify(contractService).update(eq(100L), contractCaptor.capture());
        assertEquals(ContractStatus.TERMINATED, contractCaptor.getValue().getStatus());
        verify(contractSagaPublisher).publishContractCancelled(100L, 10L);
    }

    private Message messageFor(Object payload, String routingKey) throws Exception {
        MessageProperties properties = new MessageProperties();
        properties.setReceivedRoutingKey(routingKey);
        properties.setHeader("correlationId", "contract-saga-consumer-test");
        return new Message(objectMapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8), properties);
    }
}
