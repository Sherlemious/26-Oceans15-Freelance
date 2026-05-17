package com.team26.freelance.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team26.freelance.wallet.adapter.FreelancerPayoutSummaryObjectArrayAdapter;
import com.team26.freelance.wallet.adapter.PromoCodeUsageObjectArrayAdapter;
import com.team26.freelance.wallet.model.PayoutStatus;
import com.team26.freelance.wallet.repository.PayoutRepository;
import java.math.BigDecimal;
import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class PayoutServiceTest {

  private PayoutRepository payoutRepository;
  private WalletReadClientService walletReadClientService;
  private PayoutService payoutService;

  @BeforeEach
  void setUp() {
    payoutRepository = payoutRepositoryReturningNoSummaryRows();
    walletReadClientService = mock(WalletReadClientService.class);
    payoutService = payoutServiceWith(payoutRepository, walletReadClientService);
  }

  @Test
  void getFreelancerPayoutSummaryThrowsNotFoundWhenFreelancerHasNoPayouts() {
    Long freelancerId = 99999L;

    assertThatThrownBy(
            () -> payoutService.getFreelancerPayoutSummary(freelancerId))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            ex -> {
              assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
              assertThat(ex.getReason())
                  .isEqualTo("Freelancer not found or has no payouts");
            });
    verify(walletReadClientService).getUser(freelancerId);
  }

  @Test
  void getFreelancerPayoutSummaryPropagatesUserValidationFailure() {
    Long freelancerId = 222L;
    when(walletReadClientService.getUser(freelancerId))
        .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: 222"));

    assertThatThrownBy(() -> payoutService.getFreelancerPayoutSummary(freelancerId))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            ex -> {
              assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
              assertThat(ex.getReason()).isEqualTo("User not found: 222");
            });
  }

  @Test
  void getCompletedPayoutTotalByFreelancerReturnsCompletedTotalInDateRange() {
    PayoutRepository repository = mock(PayoutRepository.class);
    PayoutService service = payoutServiceWith(repository, mock(WalletReadClientService.class));
    LocalDate startDate = LocalDate.of(2026, 3, 1);
    LocalDate endDate = LocalDate.of(2026, 3, 31);

    when(repository.getCompletedPayoutTotalByFreelancer(
            eq(1L),
            eq(PayoutStatus.COMPLETED),
            any(LocalDateTime.class),
            any(LocalDateTime.class)))
        .thenReturn(4800.0);

    BigDecimal total = service.getCompletedPayoutTotalByFreelancer(1L, startDate, endDate);

    assertThat(total).isEqualByComparingTo("4800.0");
    verify(repository)
        .getCompletedPayoutTotalByFreelancer(
            1L,
            PayoutStatus.COMPLETED,
            LocalDateTime.of(2026, 3, 1, 0, 0),
            LocalDateTime.of(2026, 4, 1, 0, 0));
  }

  @Test
  void getCompletedPayoutTotalByFreelancerReturnsZeroWhenRepositoryHasNoRows() {
    PayoutRepository repository = mock(PayoutRepository.class);
    PayoutService service = payoutServiceWith(repository, mock(WalletReadClientService.class));

    when(repository.getCompletedPayoutTotalByFreelancer(
            eq(999L),
            eq(PayoutStatus.COMPLETED),
            any(LocalDateTime.class),
            any(LocalDateTime.class)))
        .thenReturn(null);

    BigDecimal total = service.getCompletedPayoutTotalByFreelancer(
        999L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

    assertThat(total.toPlainString()).isEqualTo("0.0");
  }

  @Test
  void getCompletedPayoutTotalByFreelancerRejectsInvalidDateRange() {
    PayoutRepository repository = mock(PayoutRepository.class);
    PayoutService service = payoutServiceWith(repository, mock(WalletReadClientService.class));

    assertThatThrownBy(
            () -> service.getCompletedPayoutTotalByFreelancer(
                1L, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 3, 31)))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            ex -> {
              assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
              assertThat(ex.getReason())
                  .isEqualTo("startDate must be before or equal to endDate");
            });
  }

  private static PayoutRepository payoutRepositoryReturningNoSummaryRows() {
    return (PayoutRepository)
        Proxy.newProxyInstance(
            PayoutRepository.class.getClassLoader(),
            new Class<?>[] {PayoutRepository.class},
            (proxy, method, args) -> {
              if ("getPayoutSummaryByFreelancer".equals(method.getName())) {
                return List.of();
              }
              throw new UnsupportedOperationException(method.getName());
            });
  }

  private static PayoutService payoutServiceWith(PayoutRepository payoutRepository,
                                                 WalletReadClientService walletReadClientService) {
    return new PayoutService(
        payoutRepository,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        walletReadClientService,
        new FreelancerPayoutSummaryObjectArrayAdapter(),
        new PromoCodeUsageObjectArrayAdapter());
  }
}
