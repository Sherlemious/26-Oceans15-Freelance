package com.team26.freelance.contract.messaging;

import com.team26.freelance.contract.model.Contract;
import com.team26.freelance.contract.model.ContractStatus;
import com.team26.freelance.contract.service.ContractService;
import com.team26.freelance.contracts.dto.ContractDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

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

    @BeforeEach
    void setUp() {
        consumer = new com.team26.freelance.contract.messaging.consumers.ContractSagaConsumer(contractService, contractSagaPublisher);
    }

    @Test
    void handleProposalAcceptedCreatesContractWhenNoneExists() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("proposalId", 10L);
        payload.put("jobId", 20L);
        payload.put("freelancerId", 30L);
        payload.put("clientId", 40L);
        payload.put("bidAmount", 500.0);

        when(contractService.getActiveContractForProposal(10L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        consumer.handleProposalEvents(payload, null, "proposal.accepted");

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
    void handleProposalAcceptedSkipsCreationWhenActiveContractExists() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("proposalId", 10L);

        when(contractService.getActiveContractForProposal(10L)).thenReturn(new ContractDTO());

        consumer.handleProposalEvents(payload, null, "proposal.accepted");

        verify(contractService, never()).createContract(any());
    }

    @Test
    void handleProposalCompletedUpdatesContractStatus() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("proposalId", 10L);

        ContractDTO activeContract = new ContractDTO();
        activeContract.setId(100L);
        when(contractService.getActiveContractForProposal(10L)).thenReturn(activeContract);

        consumer.handleProposalEvents(payload, null, "proposal.completed");

        ArgumentCaptor<Contract> contractCaptor = ArgumentCaptor.forClass(Contract.class);
        verify(contractService).update(eq(100L), contractCaptor.capture());
        assertEquals(ContractStatus.COMPLETED, contractCaptor.getValue().getStatus());
    }

    @Test
    void handleProposalCancelledUpdatesStatusAndPublishesEvent() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("proposalId", 10L);

        ContractDTO activeContract = new ContractDTO();
        activeContract.setId(100L);
        when(contractService.getActiveContractForProposal(10L)).thenReturn(activeContract);

        consumer.handleProposalEvents(payload, null, "proposal.cancelled");

        ArgumentCaptor<Contract> contractCaptor = ArgumentCaptor.forClass(Contract.class);
        verify(contractService).update(eq(100L), contractCaptor.capture());
        assertEquals(ContractStatus.TERMINATED, contractCaptor.getValue().getStatus());
        verify(contractSagaPublisher).publishContractCancelled(100L, 10L);
    }
}
