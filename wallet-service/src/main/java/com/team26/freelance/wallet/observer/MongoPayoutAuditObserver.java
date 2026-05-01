package com.team26.freelance.wallet.observer;

import com.team26.freelance.common.event.EventFactory;
import com.team26.freelance.common.event.EventType;
import com.team26.freelance.common.event.PayoutAuditEvent;
import com.team26.freelance.wallet.repository.PayoutAuditEventRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MongoPayoutAuditObserver implements EntityObserver {

    private static final Logger log = LoggerFactory.getLogger(MongoPayoutAuditObserver.class);

    private final PayoutAuditEventRepository auditEventRepository;

    public MongoPayoutAuditObserver(PayoutAuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @Override
    public void onEvent(String action, Map<String, Object> payload) {
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            if (payload != null) {
                params.putAll(payload);
            }
            params.put("action", action);
            PayoutAuditEvent event = (PayoutAuditEvent) EventFactory.createEvent(EventType.PAYOUT_AUDIT, params);
            auditEventRepository.save(event);
        } catch (RuntimeException ex) {
            Object payoutId = payload == null ? null : payload.get("payoutId");
            log.warn("Failed to persist payout audit event {} for payout {}", action, payoutId, ex);
        }
    }
}
