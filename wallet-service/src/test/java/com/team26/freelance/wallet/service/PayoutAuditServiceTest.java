package com.team26.freelance.wallet.service;

import com.team26.freelance.wallet.model.Payout;
import com.team26.freelance.wallet.model.PayoutAuditEvent;
import com.team26.freelance.wallet.model.PayoutAuditEventType;
import com.team26.freelance.wallet.model.PayoutMethod;
import com.team26.freelance.wallet.observer.MongoPayoutAuditObserver;
import com.team26.freelance.wallet.observer.PayoutAuditSubject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PayoutAuditServiceTest {

    @Mock
    private PayoutAuditSubject payoutAuditSubject;

    @Mock
    private MongoPayoutAuditObserver mongoPayoutAuditObserver;

    @Mock
    private PayoutAnalyticsCacheService cacheService;

    private PayoutAuditService payoutAuditService;

    @BeforeEach
    void setUp() {
        payoutAuditService = new PayoutAuditService(payoutAuditSubject, mongoPayoutAuditObserver, cacheService);
    }

    @Test
    void recordLifecycleEventShouldNotifyObserversAndEvictCache() {
        Payout payout = buildPayout();

        payoutAuditService.recordLifecycleEvent(payout, PayoutAuditEventType.COMPLETED, "done");

        ArgumentCaptor<PayoutAuditEvent> captor = ArgumentCaptor.forClass(PayoutAuditEvent.class);
        verify(payoutAuditSubject).notifyObservers(captor.capture());
        verify(cacheService).evictMethodBreakdown();

        PayoutAuditEvent event = captor.getValue();
        assertEquals(42L, event.getPayoutId());
        assertEquals("COMPLETED", event.getEventType());
        assertEquals("PAYPAL", event.getPayoutMethod());
        assertEquals(150.0, event.getAmount());
        assertEquals("done", event.getReason());
    }

    @Test
    void recordAnalyticsViewedShouldNotEvictCache() {
        payoutAuditService.recordAnalyticsViewed();

        ArgumentCaptor<PayoutAuditEvent> captor = ArgumentCaptor.forClass(PayoutAuditEvent.class);
        verify(payoutAuditSubject).notifyObservers(captor.capture());
        verify(cacheService, never()).evictMethodBreakdown();

        PayoutAuditEvent event = captor.getValue();
        assertEquals("ANALYTICS_VIEWED", event.getEventType());
        assertNull(event.getPayoutMethod());
    }

    private Payout buildPayout() {
        Payout payout = new Payout();
        payout.setId(42L);
        payout.setMethod(PayoutMethod.PAYPAL);
        payout.setAmount(150.0);
        return payout;
    }
}
