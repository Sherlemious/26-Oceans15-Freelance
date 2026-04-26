package com.team26.freelance.wallet.service;

import com.team26.freelance.wallet.model.Payout;
import com.team26.freelance.wallet.model.PayoutAuditEvent;
import com.team26.freelance.wallet.model.PayoutAuditEventType;
import com.team26.freelance.wallet.observer.PayoutAuditSubject;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

@Service
public class PayoutAuditService {

    private final PayoutAuditSubject payoutAuditSubject;
    private final PayoutAnalyticsCacheService cacheService;

    public PayoutAuditService(PayoutAuditSubject payoutAuditSubject,
                              PayoutAnalyticsCacheService cacheService) {
        this.payoutAuditSubject = payoutAuditSubject;
        this.cacheService = cacheService;
    }

    public void recordLifecycleEvent(Payout payout, PayoutAuditEventType eventType, String reason) {
        PayoutAuditEvent event = buildEvent(payout, eventType, reason);
        payoutAuditSubject.notifyObservers(event);
        if (eventType.invalidatesMethodBreakdownCache()) {
            cacheService.evictMethodBreakdown();
        }
    }

    public void recordAnalyticsViewed() {
        PayoutAuditEvent event = new PayoutAuditEvent();
        event.setEventType(PayoutAuditEventType.ANALYTICS_VIEWED.name());
        event.setReason("Payout method breakdown analytics viewed");
        event.setTimestamp(LocalDateTime.now());
        payoutAuditSubject.notifyObservers(event);
    }

    public void recordRefundResult(Payout payout, boolean approved, Double amountReturned, String strategyApplied, String reason) {
        PayoutAuditEvent event = buildEvent(
            payout,
            approved ? PayoutAuditEventType.REFUNDED : PayoutAuditEventType.REFUND_DENIED,
            reason
        );
        event.setAmountReturned(amountReturned);
        event.setStrategyApplied(strategyApplied);
        payoutAuditSubject.notifyObservers(event);
        if (approved) {
            cacheService.evictMethodBreakdown();
        }
    }

    private PayoutAuditEvent buildEvent(Payout payout, PayoutAuditEventType eventType, String reason) {
        PayoutAuditEvent event = new PayoutAuditEvent();
        event.setPayoutId(payout.getId());
        event.setEventType(eventType.name());
        event.setPayoutMethod(payout.getMethod() == null ? null : payout.getMethod().name());
        event.setAmount(payout.getAmount());
        event.setReason(reason);
        event.setTimestamp(LocalDateTime.now());
        return event;
    }
}
