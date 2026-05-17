package com.team26.freelance.contract.controller;

import com.team26.freelance.contract.service.ContractAnalyticsService;
import com.team26.freelance.contract.service.ContractService;
import com.team26.freelance.contracts.dto.ContractDTO;
import com.team26.freelance.contracts.dto.UserContractSummaryDTO;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpStatus;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContractControllerReadEndpointsTest {

    @Mock
    private ContractService contractService;

    @Mock
    private ContractAnalyticsService contractAnalyticsService;

    private ContractController controller;

    @BeforeEach
    void setUp() {
        controller = new ContractController(contractService, contractAnalyticsService);
    }

    @Test
    void exposesUserSummaryEndpoint() {
        UserContractSummaryDTO summary = new UserContractSummaryDTO(
                5L, 3L, 1L, new BigDecimal("3000.00"), new BigDecimal("1000.00"));
        when(contractService.getUserContractSummary(7L)).thenReturn(summary);

        ResponseEntity<UserContractSummaryDTO> response = controller.getUserContractSummary(7L);

        assertEquals(summary, response.getBody());
    }

    @Test
    void exposesCountEndpoints() {
        when(contractService.getActiveContractCountForUser(7L)).thenReturn(2);
        when(contractService.getCompletedContractCountForUser(7L)).thenReturn(4L);
        when(contractService.getActiveContractCountForJob(9L)).thenReturn(1);

        assertEquals(2, controller.getActiveContractCountForUser(7L).getBody());
        assertEquals(4L, controller.getCompletedContractCountForUser(7L).getBody());
        assertEquals(1, controller.getActiveContractCountForJob(9L).getBody());
    }

    @Test
    void exposesProposalActiveAndContractDetailAsContractDto() {
        ContractDTO active = new ContractDTO(
                3L,
                9L,
                7L,
                2L,
                11L,
                1200.0,
                "ACTIVE",
                LocalDateTime.of(2026, 5, 1, 10, 0),
                null);
        ContractDTO detail = new ContractDTO(
                4L,
                8L,
                6L,
                3L,
                12L,
                900.0,
                "COMPLETED",
                LocalDateTime.of(2026, 4, 1, 9, 30),
                LocalDateTime.of(2026, 4, 20, 18, 0));
        when(contractService.getActiveContractForProposal(11L)).thenReturn(active);
        when(contractService.getContractDtoById(4L)).thenReturn(detail);

        assertEquals(active, controller.getActiveContractForProposal(11L).getBody());
        assertEquals(detail, controller.getContractById(4L).getBody());
    }

    @Test
    void proposalActiveEndpointPropagatesNotFoundWhenNoActiveContractExists() {
        when(contractService.getActiveContractForProposal(11L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Active contract not found"));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.getActiveContractForProposal(11L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }
}
