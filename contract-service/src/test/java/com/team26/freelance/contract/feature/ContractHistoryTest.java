package com.team26.freelance.contract.feature;

import com.team26.freelance.contract.controller.ContractController;
import com.team26.freelance.contract.model.Contract;
import com.team26.freelance.contract.model.ContractStatus;
import com.team26.freelance.contract.repository.ContractRepository;
import com.team26.freelance.contract.service.ContractService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ContractController.class)
@Import(ContractService.class)
class ContractHistoryTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ContractRepository contractRepository;

    private List<Contract> contracts;

    @BeforeEach
    void setUp() {
        contracts = new ArrayList<>();

        // 2 contracts in February
        contracts.add(createContract(1L, 10L, 100L, 1000L, ContractStatus.ACTIVE, LocalDateTime.of(2026, 2, 5, 9, 0)));
        contracts.add(createContract(2L, 11L, 101L, 1001L, ContractStatus.COMPLETED, LocalDateTime.of(2026, 2, 28, 17, 0)));

        // 3 contracts in March (2 ACTIVE, 1 COMPLETED)
        contracts.add(createContract(3L, 12L, 102L, 1002L, ContractStatus.ACTIVE, LocalDateTime.of(2026, 3, 20, 12, 30)));
        contracts.add(createContract(4L, 13L, 103L, 1003L, ContractStatus.COMPLETED, LocalDateTime.of(2026, 3, 5, 9, 15)));
        contracts.add(createContract(5L, 14L, 104L, 1004L, ContractStatus.ACTIVE, LocalDateTime.of(2026, 3, 10, 14, 45)));

        when(contractRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(
                ArgumentMatchers.any(LocalDateTime.class),
                ArgumentMatchers.any(LocalDateTime.class)
        )).thenAnswer(invocation -> {
            LocalDateTime start = invocation.getArgument(0);
            LocalDateTime end = invocation.getArgument(1);
            return contracts.stream()
                    .filter(c -> !c.getCreatedAt().isBefore(start) && !c.getCreatedAt().isAfter(end))
                    .sorted(Comparator.comparing(Contract::getCreatedAt))
                    .toList();
        });

        when(contractRepository.findByCreatedAtBetweenAndStatusOrderByCreatedAtAsc(
                ArgumentMatchers.any(LocalDateTime.class),
                ArgumentMatchers.any(LocalDateTime.class),
                ArgumentMatchers.any(ContractStatus.class)
        )).thenAnswer(invocation -> {
            LocalDateTime start = invocation.getArgument(0);
            LocalDateTime end = invocation.getArgument(1);
            ContractStatus status = invocation.getArgument(2);
            return contracts.stream()
                    .filter(c -> !c.getCreatedAt().isBefore(start) && !c.getCreatedAt().isAfter(end))
                    .filter(c -> c.getStatus() == status)
                    .sorted(Comparator.comparing(Contract::getCreatedAt))
                    .toList();
        });
    }

    @Test
    void shouldReturnMarchContractsOrderedByCreatedAtAscending() throws Exception {
        mockMvc.perform(get("/api/contracts/history")
                        .param("startDate", "2026-03-01")
                        .param("endDate", "2026-03-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].createdAt", startsWith("2026-03-05T09:15:00")))
                .andExpect(jsonPath("$[1].createdAt", startsWith("2026-03-10T14:45:00")))
                .andExpect(jsonPath("$[2].createdAt", startsWith("2026-03-20T12:30:00")));
    }

    @Test
    void shouldReturnOnlyActiveMarchContractsWhenStatusFilterProvided() throws Exception {
        mockMvc.perform(get("/api/contracts/history")
                        .param("startDate", "2026-03-01")
                        .param("endDate", "2026-03-31")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void shouldReturnEmptyListWhenNoContractsInDateRange() throws Exception {
        mockMvc.perform(get("/api/contracts/history")
                        .param("startDate", "2026-01-01")
                        .param("endDate", "2026-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    private Contract createContract(
            Long jobId,
            Long freelancerId,
            Long clientId,
            Long proposalId,
            ContractStatus status,
            LocalDateTime createdAt
    ) {
        Contract contract = new Contract();
        contract.setJobId(jobId);
        contract.setFreelancerId(freelancerId);
        contract.setClientId(clientId);
        contract.setProposalId(proposalId);
        contract.setAgreedAmount(500.0);
        contract.setStatus(status);
        contract.setStartDate(createdAt.minusDays(1));
        contract.setEndDate(null);
        contract.setCreatedAt(createdAt);
        return contract;
    }
}
