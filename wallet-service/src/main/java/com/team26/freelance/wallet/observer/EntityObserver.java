package com.team26.freelance.wallet.observer;

public interface EntityObserver<T> {
    void onEvent(T event);
}
