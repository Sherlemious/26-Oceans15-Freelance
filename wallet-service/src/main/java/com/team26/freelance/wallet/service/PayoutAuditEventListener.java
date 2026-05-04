package com.team26.freelance.wallet.service;

import com.team26.freelance.common.event.PayoutAuditEvent;
import com.team26.freelance.wallet.repository.PayoutAuditEventRepository;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PayoutAuditEventListener {

    private final PayoutAuditEventRepository auditEventRepository;

    public PayoutAuditEventListener(PayoutAuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPayoutAuditPending(PayoutAuditPendingEvent event) {
        Map<String, Object> params = new HashMap<>();
        params.put("payoutId", event.getPayoutId());
        params.put("action", event.getAction());
        params.put("timestamp", event.getTimestamp());
        params.put("method", event.getMethod());
        params.put("amount", event.getAmount());
        params.put("details", event.getDetails());

        auditEventRepository.save(new PayoutAuditEvent(params));
    }
}