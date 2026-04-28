package com.team26.freelance.wallet.service;

import com.team26.freelance.wallet.model.PayoutAuditEvent;
import com.team26.freelance.wallet.repository.PayoutAuditEventRepository;
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
        PayoutAuditEvent auditEvent = new PayoutAuditEvent();
        auditEvent.setPayoutId(event.getPayoutId());
        auditEvent.setEventType(event.getEventType());
        auditEvent.setAmountReturned(event.getAmountReturned());
        auditEvent.setStrategyApplied(event.getStrategyApplied());
        auditEvent.setReason(event.getReason());
        auditEvent.setTimestamp(event.getTimestamp());
        auditEventRepository.save(auditEvent);
    }
}
