package com.team26.freelance.wallet.observer;

import com.team26.freelance.common.event.EventFactory;
import com.team26.freelance.common.event.EventType;
import com.team26.freelance.common.event.MongoEvent;
import com.team26.freelance.common.event.PayoutAuditEvent;
import com.team26.freelance.wallet.repository.PayoutAuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class MongoEventLogger implements EntityObserver {

    private static final Logger log = LoggerFactory.getLogger(MongoEventLogger.class);

    private final PayoutAuditEventRepository auditEventRepository;
    private final EventType eventType = EventType.PAYOUT_AUDIT;

    public MongoEventLogger(PayoutAuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @Override
    public void onEvent(String eventType, Object payload) {
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            if (payload instanceof Map<?, ?> payloadMap) {
                payloadMap.forEach((key, value) -> {
                    if (key != null) {
                        params.put(key.toString(), value);
                    }
                });
            } else if (payload != null) {
                params.put("details", Map.of("payload", payload));
            }
            params.put("action", eventType);

            MongoEvent event = EventFactory.createEvent(this.eventType, params);
            auditEventRepository.save((PayoutAuditEvent) event);
        } catch (RuntimeException ex) {
            log.warn("Failed to persist payout audit event {}", eventType, ex);
        }
    }
}
