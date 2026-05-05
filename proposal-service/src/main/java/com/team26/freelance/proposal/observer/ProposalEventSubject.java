package com.team26.freelance.proposal.observer;

import org.springframework.stereotype.Component;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class ProposalEventSubject {
    private final List<EntityObserver> observers;

    // Spring will automatically inject ALL classes that implement EntityObserver
    public ProposalEventSubject(List<EntityObserver> observers) {
        // Thread-safe list
        this.observers = new CopyOnWriteArrayList<>(observers);
    }

    public void register(EntityObserver observer) {
        observers.add(observer);
    }

    public void notifyObservers(String eventType, Object payload) {
        for (EntityObserver observer : observers) {
            try {
                // Try-catch ensures one broken observer doesn't crash the rest
                observer.onEvent(eventType, payload);
            } catch (Exception e) {
                System.err.println("WARN: Observer failed: " + e.getMessage());
            }
        }
    }
}