package com.team26.freelance.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team26.freelance.contracts.dto.ContractDTO;
import com.team26.freelance.contracts.dto.JobDTO;
import com.team26.freelance.wallet.dto.CategoryRevenueDTO;
import com.team26.freelance.wallet.model.Payout;
import com.team26.freelance.wallet.model.PayoutStatus;
import com.team26.freelance.wallet.repository.PayoutRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class PlatformFeeAnalyticsServiceTest {

    @Test
    void aggregatesCategoryRevenueWithFallbackAndRequestScopeCaches() {
        PayoutRepository payoutRepository = mock(PayoutRepository.class);
        WalletReadClientService walletReadClientService = mock(WalletReadClientService.class);
        PlatformFeeAnalyticsService service = new PlatformFeeAnalyticsService(
                payoutRepository,
                walletReadClientService
        );

        Payout payoutOne = payout(1L, 200.0, Map.of("platformFee", 30.0));
        Payout payoutTwo = payout(1L, 100.0, Map.of());
        Payout payoutThree = payout(2L, 50.0, Map.of("platformFee", "5.0"));

        when(payoutRepository.findByStatusAndCreatedAtRange(any(), any(), any()))
                .thenReturn(List.of(payoutOne, payoutTwo, payoutThree));

        when(walletReadClientService.getContract(1L)).thenReturn(contract(1L, 11L));
        when(walletReadClientService.getContract(2L)).thenReturn(contract(2L, 22L));
        when(walletReadClientService.getJob(11L)).thenReturn(job(11L, "DESIGN"));
        when(walletReadClientService.getJob(22L)).thenReturn(job(22L, "ENGINEERING"));

        List<CategoryRevenueDTO> result = service.getPlatformFeeAnalytics(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31)
        );

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCategory()).isEqualTo("DESIGN");
        assertThat(result.get(0).getTotalRevenue()).isEqualTo(300.0);
        assertThat(result.get(0).getPlatformFeeRevenue()).isEqualTo(40.0);
        assertThat(result.get(0).getNetPayoutRevenue()).isEqualTo(260.0);
        assertThat(result.get(0).getPayoutCount()).isEqualTo(2L);

        assertThat(result.get(1).getCategory()).isEqualTo("ENGINEERING");
        assertThat(result.get(1).getTotalRevenue()).isEqualTo(50.0);
        assertThat(result.get(1).getPlatformFeeRevenue()).isEqualTo(5.0);
        assertThat(result.get(1).getNetPayoutRevenue()).isEqualTo(45.0);
        assertThat(result.get(1).getPayoutCount()).isEqualTo(1L);

        verify(payoutRepository, times(1)).findByStatusAndCreatedAtRange(
                PayoutStatus.COMPLETED,
                LocalDateTime.of(2026, 1, 1, 0, 0),
                LocalDateTime.of(2026, 2, 1, 0, 0)
        );
        verify(walletReadClientService, times(1)).getContract(1L);
        verify(walletReadClientService, times(1)).getJob(11L);
    }

    @Test
    void usesUnknownCategoryWhenFeignCallFails() {
        PayoutRepository payoutRepository = mock(PayoutRepository.class);
        WalletReadClientService walletReadClientService = mock(WalletReadClientService.class);
        PlatformFeeAnalyticsService service = new PlatformFeeAnalyticsService(
                payoutRepository,
                walletReadClientService
        );

        when(payoutRepository.findByStatusAndCreatedAtRange(any(), any(), any()))
                .thenReturn(List.of(payout(55L, 120.0, Map.of())));
        when(walletReadClientService.getContract(55L)).thenThrow(
                new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "contract service temporarily unavailable")
        );

        List<CategoryRevenueDTO> result = service.getPlatformFeeAnalytics(
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 2, 1)
        );

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCategory()).isEqualTo("UNKNOWN");
        assertThat(result.get(0).getPlatformFeeRevenue()).isEqualTo(12.0);
    }

    @Test
    void rejectsInvalidDateRange() {
        PlatformFeeAnalyticsService service = new PlatformFeeAnalyticsService(
                mock(PayoutRepository.class),
                mock(WalletReadClientService.class)
        );

        assertThatThrownBy(() -> service.getPlatformFeeAnalytics(
                LocalDate.of(2026, 3, 2),
                LocalDate.of(2026, 3, 1)
        )).isInstanceOfSatisfying(
                ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST)
        );
    }

    private static Payout payout(Long contractId, double amount, Map<String, Object> details) {
        Payout payout = new Payout();
        payout.setContractId(contractId);
        payout.setAmount(amount);
        payout.setTransactionDetails(details);
        payout.setStatus(PayoutStatus.COMPLETED);
        return payout;
    }

    private static ContractDTO contract(Long contractId, Long jobId) {
        ContractDTO dto = new ContractDTO();
        dto.setId(contractId);
        dto.setJobId(jobId);
        return dto;
    }

    private static JobDTO job(Long jobId, String category) {
        JobDTO dto = new JobDTO();
        dto.setId(jobId);
        dto.setCategory(category);
        return dto;
    }
}
