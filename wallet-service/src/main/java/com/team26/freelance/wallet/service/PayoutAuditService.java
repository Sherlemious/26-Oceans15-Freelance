package com.team26.freelance.wallet.service;

import com.team26.freelance.wallet.model.Payout;
import com.team26.freelance.wallet.model.PayoutMethod;
import com.team26.freelance.wallet.observer.PayoutAuditSubject;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class PayoutAuditService {

    public static final String CREATED = "CREATED";
    public static final String COMPLETED = "COMPLETED";
    public static final String FAILED = "FAILED";
    public static final String REFUNDED = "REFUNDED";
    public static final String REFUND_DENIED = "REFUND_DENIED";
    public static final String ANALYTICS_VIEWED = "ANALYTICS_VIEWED";
    public static final String PROMO_APPLIED = "PROMO_APPLIED";
    public static final String RETRY_ATTEMPTED = "RETRY_ATTEMPTED";
    public static final String PAYOUT_CREATED = "PAYOUT_CREATED";
    public static final String PAYOUT_UPDATED = "PAYOUT_UPDATED";
    public static final String PAYOUT_DELETED = "PAYOUT_DELETED";
    public static final String PROMO_CODE_CREATED = "PROMO_CODE_CREATED";
    public static final String PROMO_CODE_UPDATED = "PROMO_CODE_UPDATED";
    public static final String PROMO_CODE_DELETED = "PROMO_CODE_DELETED";
    public static final String PAYOUT_PROMO_CREATED = "PAYOUT_PROMO_CREATED";
    public static final String PAYOUT_PROMO_DELETED = "PAYOUT_PROMO_DELETED";

    private final PayoutAuditSubject payoutAuditSubject;
    private final PayoutAnalyticsCacheService cacheService;

    public PayoutAuditService(PayoutAuditSubject payoutAuditSubject,
                              PayoutAnalyticsCacheService cacheService) {
        this.payoutAuditSubject = payoutAuditSubject;
        this.cacheService = cacheService;
    }

    public void recordPayoutEvent(Payout payout, String action, Map<String, Object> details) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (payout != null) {
            payload.put("payoutId", payout.getId());
            payload.put("method", methodName(payout));
            payload.put("amount", payout.getAmount());
        }
        payload.put("details", copyDetails(details));
        record(action, payload);
    }

    public void recordAnalyticsViewed() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("payoutId", null);
        payload.put("action", ANALYTICS_VIEWED);
        payload.put("details", Map.of("reason", "Payout analytics viewed"));
        record(ANALYTICS_VIEWED, payload);
    }

    public void recordRefundResult(Payout payout,
                                   boolean approved,
                                   Double amountReturned,
                                   String strategyApplied,
                                   String reasonCode,
                                   String reversalScope,
                                   String refundReason) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("amountReturned", amountReturned);
        details.put("strategyApplied", strategyApplied);
        details.put("reasonCode", reasonCode);
        details.put("originalAmount", payout == null ? null : payout.getAmount());
        details.put("reversalScope", reversalScope);
        details.put("refundReason", refundReason);

        recordPayoutEvent(payout, approved ? REFUNDED : REFUND_DENIED, details);
        if (payout != null) {
            cacheService.evictPayoutDetail(payout.getId());
        }
    }

    public void recordGenericEvent(String action, Map<String, Object> details) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("details", copyDetails(details));
        record(action, payload);
    }

    private void record(String action, Map<String, Object> payload) {
        payoutAuditSubject.notifyObservers(action, payload);
        if (invalidatesAnalyticsCache(action)) {
            cacheService.evictAnalyticsCaches();
        }
    }

    private boolean invalidatesAnalyticsCache(String action) {
        if (ANALYTICS_VIEWED.equals(action)) {
            return false;
        }
        return true;
    }

    private String methodName(Payout payout) {
        if (payout.getMethod() == null) {
            return null;
        }
        if (payout.getMethod() == PayoutMethod.BANK) {
            return PayoutMethod.BANK_TRANSFER.name();
        }
        return payout.getMethod().name();
    }

    private Map<String, Object> copyDetails(Map<String, Object> details) {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (details == null) {
            return copy;
        }
        details.forEach((key, value) -> {
            if (key != null) {
                copy.put(key, value);
            }
        });
        return copy;
    }
}
