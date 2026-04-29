package com.team26.freelance.job.observer;

public interface EntityObserver {
    void onEvent(String eventType, Object payload);
}
