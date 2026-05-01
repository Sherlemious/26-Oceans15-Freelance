package com.team26.freelance.wallet.service;

import com.team26.freelance.wallet.model.EventFactory;
import com.team26.freelance.wallet.model.EventType;
import com.team26.freelance.wallet.model.Payout;
import com.team26.freelance.wallet.model.PayoutAuditEvent;
import com.team26.freelance.wallet.model.PayoutAuditEventType;
import com.team26.freelance.wallet.observer.PayoutAuditSubject;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
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
        Map<String, Object> params = new HashMap<>();
        params.put("action", PayoutAuditEventType.ANALYTICS_VIEWED.name());
        params.put("eventType", PayoutAuditEventType.ANALYTICS_VIEWED.name());
        params.put("reason", "Payout method breakdown analytics viewed");
        params.put("timestamp", LocalDateTime.now());
        PayoutAuditEvent event = (PayoutAuditEvent) EventFactory.createEvent(EventType.PAYOUT_AUDIT, params);
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
        Map<String, Object> params = new HashMap<>();
        params.put("payoutId", payout.getId());
        params.put("action", eventType.name());
        params.put("eventType", eventType.name());
        params.put("method", payout.getMethod() == null ? null : payout.getMethod().name());
        params.put("amount", payout.getAmount());
        params.put("reason", reason);
        params.put("timestamp", LocalDateTime.now());
        return (PayoutAuditEvent) EventFactory.createEvent(EventType.PAYOUT_AUDIT, params);
    }
}
