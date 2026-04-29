package com.team26.freelance.contract.observer;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class ContractEventSubject {
    private final List<EntityObserver> observers = new CopyOnWriteArrayList<>();

    public void register(EntityObserver observer) {
        if (observer != null && !observers.contains(observer)) {
            observers.add(observer);
        }
    }

    public void unregister(EntityObserver observer) {
        observers.remove(observer);
    }

    public void notifyObservers(Long contractId, String type, Map<String, Object> details) {
        for (EntityObserver observer : observers) {
            observer.onEvent(contractId, type, details);
        }
    }
}
