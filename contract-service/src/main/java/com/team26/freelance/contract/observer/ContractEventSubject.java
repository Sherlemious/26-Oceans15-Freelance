package com.team26.freelance.contract.observer;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ContractEventSubject {
    
    private final List<EntityObserver> observers;

    public ContractEventSubject(List<EntityObserver> observers) {
        this.observers = new ArrayList<>(observers);
    }
    
    public void register(EntityObserver observer) {
        observers.add(observer);
    }
    
    public void unregister(EntityObserver observer) {
        observers.remove(observer);
    }

    public void notifyObservers(String eventType, Object payload) {
        observers.forEach(observer -> observer.onEvent(eventType, payload));
    }
}
