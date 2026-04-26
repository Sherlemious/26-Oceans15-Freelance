package com.team26.freelance.wallet.observer;

import com.team26.freelance.wallet.model.PayoutAuditEvent;
import com.team26.freelance.wallet.repository.PayoutAuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MongoPayoutAuditObserver implements EntityObserver<PayoutAuditEvent> {

    private static final Logger log = LoggerFactory.getLogger(MongoPayoutAuditObserver.class);

    private final PayoutAuditEventRepository auditEventRepository;

    public MongoPayoutAuditObserver(PayoutAuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @Override
    public void onEvent(PayoutAuditEvent event) {
        try {
            auditEventRepository.save(event);
        } catch (RuntimeException ex) {
            log.warn("Failed to persist payout audit event {} for payout {}", event.getEventType(), event.getPayoutId(), ex);
        }
    }
}
