package com.team26.freelance.user.observer;

public interface EntityObserver {
    void onEvent(String eventType, Object payload);
}
