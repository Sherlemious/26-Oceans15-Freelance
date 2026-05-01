package com.team26.freelance.wallet.observer;

import java.util.Map;

public interface EntityObserver {
    void onEvent(String action, Map<String, Object> payload);
}
