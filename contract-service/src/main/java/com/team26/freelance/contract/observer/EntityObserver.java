package com.team26.freelance.contract.observer;

import java.util.Map;

public interface EntityObserver {
    void onEvent(Long contractId, String type, Map<String, Object> details);
}
