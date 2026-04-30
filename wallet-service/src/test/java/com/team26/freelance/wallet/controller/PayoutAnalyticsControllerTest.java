package com.team26.freelance.wallet.controller;

import com.team26.freelance.wallet.dto.PayoutMethodBreakdownDTO;
import com.team26.freelance.wallet.service.PayoutAnalyticsService;
import com.team26.freelance.wallet.service.WalletJwtService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayoutAnalyticsControllerTest {

    @Mock
    private PayoutAnalyticsService payoutAnalyticsService;

    @Mock
    private WalletJwtService walletJwtService;

    @Test
    void getMethodBreakdownShouldRequireBearerToken() {
        PayoutAnalyticsController controller = new PayoutAnalyticsController(payoutAnalyticsService, walletJwtService);

        whenUnauthorized(null);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.getMethodBreakdown(null));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
    }

    @Test
    void getMethodBreakdownShouldReturnAnalyticsWhenAuthorized() {
        PayoutAnalyticsController controller = new PayoutAnalyticsController(payoutAnalyticsService, walletJwtService);
        List<PayoutMethodBreakdownDTO> breakdown = List.of(
                new PayoutMethodBreakdownDTO("BANK_TRANSFER", BigDecimal.TEN, 1, BigDecimal.TEN, 100.0)
        );
        when(payoutAnalyticsService.getMethodBreakdown()).thenReturn(breakdown);

        ResponseEntity<List<PayoutMethodBreakdownDTO>> response = controller.getMethodBreakdown("Bearer token");

        assertEquals(breakdown, response.getBody());
        verify(walletJwtService).validateAuthorizationHeader("Bearer token");
        verify(payoutAnalyticsService).getMethodBreakdown();
    }

    private void whenUnauthorized(String authorization) {
        org.mockito.Mockito.doThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid bearer token"))
                .when(walletJwtService).validateAuthorizationHeader(authorization);
    }
}
