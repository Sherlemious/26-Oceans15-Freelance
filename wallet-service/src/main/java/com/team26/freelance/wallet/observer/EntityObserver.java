package com.team26.freelance.wallet.observer;

public interface EntityObserver {
    void onEvent(String eventType, Object payload);
}
