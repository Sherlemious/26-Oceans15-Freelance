package com.team26.freelance.contract.service;

import com.team26.freelance.contract.adapter.CassandraRowAdapter;
import com.team26.freelance.contract.dto.ContractSummaryDTO;
import com.team26.freelance.contract.model.Contract;
import com.team26.freelance.contract.model.ContractStatus;
import com.team26.freelance.contract.observer.ContractEventSubject;
import com.team26.freelance.contract.repository.ContractRepository;
import com.team26.freelance.contract.repository.cassandra.ContractMilestoneEventRepository;
import com.team26.freelance.contracts.dto.ContractDTO;
import com.team26.freelance.contracts.dto.JobDTO;
import com.team26.freelance.contracts.dto.UserContractSummaryDTO;
import com.team26.freelance.contracts.dto.UserDTO;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.cassandra.core.CassandraTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContractServiceReadEndpointsTest {

    @Mock
    private ContractRepository contractRepository;

    @Mock
    private ContractCacheEvictionService cacheEvictionService;

    @Mock
    private ContractMilestoneEventRepository contractMilestoneEventRepository;

    @Mock
    private ContractEventSubject contractEventSubject;

    @Mock
    private ContractAnalyticsService contractAnalyticsService;

    @Mock
    private CassandraTemplate cassandraTemplate;

    @Mock
    private CassandraRowAdapter cassandraRowAdapter;

    @Mock
    private ContractReadClientService contractReadClientService;

    private ContractService service;

    @BeforeEach
    void setUp() {
        service = new ContractService(
                contractRepository,
                cacheEvictionService,
                contractMilestoneEventRepository,
                contractEventSubject,
                contractAnalyticsService,
                cassandraTemplate,
                cassandraRowAdapter,
                contractReadClientService);
    }

    @Test
    void getUserContractSummaryAggregatesLocalContractData() {
        when(contractRepository.getUserContractSummary(7L)).thenReturn(new Object[]{
                5L,
                3L,
                1L,
                new BigDecimal("3000.00"),
                new BigDecimal("1000.00")
        });

        UserContractSummaryDTO summary = service.getUserContractSummary(7L);

        assertEquals(5L, summary.getTotalContracts());
        assertEquals(3L, summary.getCompletedContracts());
        assertEquals(1L, summary.getTerminatedContracts());
        assertEquals(new BigDecimal("3000.00"), summary.getTotalEarnings());
        assertEquals(new BigDecimal("1000.00"), summary.getAverageContractValue());
    }

    @Test
    void countEndpointsUseLocalContractRepository() {
        when(contractRepository.countByFreelancerIdAndStatus(7L, ContractStatus.ACTIVE)).thenReturn(2L);
        when(contractRepository.countByFreelancerIdAndStatus(7L, ContractStatus.COMPLETED)).thenReturn(4L);
        when(contractRepository.countByJobIdAndStatus(9L, ContractStatus.ACTIVE)).thenReturn(1L);

        assertEquals(2, service.getActiveContractCountForUser(7L));
        assertEquals(4L, service.getCompletedContractCountForUser(7L));
        assertEquals(1, service.getActiveContractCountForJob(9L));
    }

    @Test
    void getActiveContractForProposalReturnsDto() {
        Contract contract = contract(
                3L,
                9L,
                7L,
                2L,
                11L,
                1200.0,
                ContractStatus.ACTIVE,
                LocalDateTime.of(2026, 5, 1, 10, 0),
                null);
        when(contractRepository.findFirstByProposalIdAndStatusOrderByCreatedAtDesc(11L, ContractStatus.ACTIVE))
                .thenReturn(Optional.of(contract));

        ContractDTO dto = service.getActiveContractForProposal(11L);

        assertEquals(3L, dto.getId());
        assertEquals(9L, dto.getJobId());
        assertEquals(7L, dto.getFreelancerId());
        assertEquals(2L, dto.getClientId());
        assertEquals(11L, dto.getProposalId());
        assertEquals(1200.0, dto.getAgreedAmount());
        assertEquals("ACTIVE", dto.getStatus());
        assertEquals(LocalDateTime.of(2026, 5, 1, 10, 0), dto.getStartDate());
    }

    @Test
    void getActiveContractForProposalReturnsNotFoundWhenAbsent() {
        when(contractRepository.findFirstByProposalIdAndStatusOrderByCreatedAtDesc(11L, ContractStatus.ACTIVE))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.getActiveContractForProposal(11L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void getContractDtoByIdIncludesRequiredFields() {
        LocalDateTime startDate = LocalDateTime.of(2026, 4, 1, 9, 30);
        LocalDateTime endDate = LocalDateTime.of(2026, 4, 20, 18, 0);
        Contract contract = contract(4L, 8L, 6L, 3L, 12L, 900.0, ContractStatus.COMPLETED, startDate, endDate);
        when(contractRepository.findById(4L)).thenReturn(Optional.of(contract));

        ContractDTO dto = service.getContractDtoById(4L);

        assertEquals(4L, dto.getId());
        assertEquals(8L, dto.getJobId());
        assertEquals(6L, dto.getFreelancerId());
        assertEquals(3L, dto.getClientId());
        assertEquals(12L, dto.getProposalId());
        assertEquals(900.0, dto.getAgreedAmount());
        assertEquals("COMPLETED", dto.getStatus());
        assertEquals(startDate, dto.getStartDate());
        assertEquals(endDate, dto.getEndDate());
    }

    @Test
    void searchContractsUsesLocalBudgetQueryAndFeignEnrichment() {
        Contract contract = contract(
                4L,
                8L,
                6L,
                3L,
                12L,
                5000.0,
                ContractStatus.ACTIVE,
                LocalDateTime.of(2026, 4, 1, 9, 30),
                null);
        UserDTO user = new UserDTO();
        user.setName("Ahmed");
        JobDTO job = new JobDTO();
        job.setTitle("Backend API");

        when(contractRepository.findByAgreedAmountBetweenAndStatusOrderByAgreedAmountDesc(
                2000.0, 6000.0, ContractStatus.ACTIVE)).thenReturn(List.of(contract));
        when(contractReadClientService.getUser(6L)).thenReturn(user);
        when(contractReadClientService.getJob(8L)).thenReturn(job);

        List<ContractSummaryDTO> summaries = service.findContractsByBudgetRangeWithFreelancerInfo(
                2000.0, 6000.0, "ACTIVE");

        assertEquals(1, summaries.size());
        assertEquals(4L, summaries.getFirst().getContractId());
        assertEquals("Ahmed", summaries.getFirst().getFreelancerName());
        assertEquals("Backend API", summaries.getFirst().getJobTitle());
        verify(contractRepository).findByAgreedAmountBetweenAndStatusOrderByAgreedAmountDesc(
                2000.0, 6000.0, ContractStatus.ACTIVE);
    }

    private Contract contract(Long id,
                              Long jobId,
                              Long freelancerId,
                              Long clientId,
                              Long proposalId,
                              Double agreedAmount,
                              ContractStatus status,
                              LocalDateTime startDate,
                              LocalDateTime endDate) {
        Contract contract = new Contract();
        contract.setId(id);
        contract.setJobId(jobId);
        contract.setFreelancerId(freelancerId);
        contract.setClientId(clientId);
        contract.setProposalId(proposalId);
        contract.setAgreedAmount(agreedAmount);
        contract.setStatus(status);
        contract.setStartDate(startDate);
        contract.setEndDate(endDate);
        contract.setCreatedAt(startDate);
        return contract;
    }
}
