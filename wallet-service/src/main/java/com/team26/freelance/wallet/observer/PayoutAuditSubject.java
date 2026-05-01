package com.team26.freelance.wallet.observer;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class PayoutAuditSubject {

    private final List<EntityObserver> observers;

    public PayoutAuditSubject(List<EntityObserver> observers) {
        this.observers = List.copyOf(observers);
    }

    public void notifyObservers(String action, Map<String, Object> payload) {
        observers.forEach(observer -> observer.onEvent(action, payload));
    }
}
