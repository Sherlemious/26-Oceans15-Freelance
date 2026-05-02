package com.team26.freelance.contract.observer;

public interface ContractEventSubject {
    void register(EntityObserver observer);
    void unregister(EntityObserver observer);
    void notifyObservers(String eventType, Object payload);
}
