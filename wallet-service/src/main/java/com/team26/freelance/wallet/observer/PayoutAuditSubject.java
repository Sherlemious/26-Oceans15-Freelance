package com.team26.freelance.wallet.observer;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PayoutAuditSubject {

    private final List<EntityObserver> observers;

    public PayoutAuditSubject(List<EntityObserver> observers) {
        this.observers = new ArrayList<>(observers);
    }

    public void registerObserver(EntityObserver observer) {
        observers.add(observer);
    }

    public void unregisterObserver(EntityObserver observer) {
        observers.remove(observer);
    }

    public void unregisterAllObservers() {
        observers.clear();
    }

    public void notifyObservers(String action, Object payload) {
        observers.forEach(observer -> observer.onEvent(action, payload));
    }
}
