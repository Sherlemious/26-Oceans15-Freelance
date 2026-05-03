package com.team26.freelance.proposal.observer;

import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

@Component
public class ProposalEventSubject {
    private final List<EntityObserver> observers = new ArrayList<>();

    public ProposalEventSubject(MongoEventLogger logger) {
        this.register(logger);
    }

    public void register(EntityObserver observer) {
        observers.add(observer);
    }

    public void unregister(EntityObserver observer) {
        observers.remove(observer);
    }

    public void notifyObservers(String eventType, Object payload) {
        for (EntityObserver observer : observers) {
            observer.onEvent(eventType, payload);
        }
    }
}