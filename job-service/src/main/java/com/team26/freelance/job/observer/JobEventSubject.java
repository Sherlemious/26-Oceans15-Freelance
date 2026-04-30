package com.team26.freelance.job.observer;

public interface JobEventSubject {
    void register(EntityObserver observer);
    void unregister(EntityObserver observer);
    void notifyObservers(String eventType, Object payload);
}