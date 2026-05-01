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
        auditEvent.setAction(event.getAction());
        auditEvent.setMethod(event.getMethod());
        auditEvent.setAmount(event.getAmount());
        auditEvent.setDetails(event.getDetails());
        auditEvent.setTimestamp(event.getTimestamp());
        auditEventRepository.save(auditEvent);
    }
}