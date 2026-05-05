package com.team26.freelance.proposal.observer;

public interface EntityObserver {
    void onEvent(String eventType, Object payload);
}