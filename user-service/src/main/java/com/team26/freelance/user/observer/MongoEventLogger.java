package com.team26.freelance.user.observer;

import com.team26.freelance.common.event.AuthEvent;
import com.team26.freelance.common.event.EventFactory;
import com.team26.freelance.common.event.EventType;
import com.team26.freelance.common.event.MongoEvent;
import com.team26.freelance.user.repository.AuthEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class MongoEventLogger implements EntityObserver {

    private static final Logger log = LoggerFactory.getLogger(MongoEventLogger.class);

    private final AuthEventRepository authEventRepository;
    private final EventType eventType = EventType.AUTH;

    public MongoEventLogger(AuthEventRepository authEventRepository) {
        this.authEventRepository = authEventRepository;
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
            authEventRepository.save((AuthEvent) event);
        } catch (DataAccessException | IllegalArgumentException ex) {
            log.warn("Failed to write auth event to MongoDB: {}", eventType, ex);
        }
    }
}
