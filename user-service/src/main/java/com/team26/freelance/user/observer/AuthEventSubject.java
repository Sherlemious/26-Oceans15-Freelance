package com.team26.freelance.user.observer;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class AuthEventSubject {

    private final List<EntityObserver> observers;

    public AuthEventSubject(List<EntityObserver> observers) {
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

    public void notifyObservers(String eventType, Object payload) {
        observers.forEach(observer -> observer.onEvent(eventType, payload));
    }
}
