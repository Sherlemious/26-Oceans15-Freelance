package com.team26.freelance.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team26.freelance.wallet.adapter.FreelancerPayoutSummaryObjectArrayAdapter;
import com.team26.freelance.wallet.adapter.PromoCodeUsageObjectArrayAdapter;
import com.team26.freelance.wallet.repository.PayoutRepository;
import java.lang.reflect.Proxy;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class PayoutServiceTest {

  private PayoutRepository payoutRepository;
  private PayoutService payoutService;

  @BeforeEach
  void setUp() {
    payoutRepository = payoutRepositoryReturningNoSummaryRows();
    payoutService =
        new PayoutService(
            payoutRepository,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            new FreelancerPayoutSummaryObjectArrayAdapter(),
            new PromoCodeUsageObjectArrayAdapter());
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
}
