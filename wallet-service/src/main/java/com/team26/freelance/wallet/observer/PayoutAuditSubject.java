package com.team26.freelance.wallet.observer;

import com.team26.freelance.wallet.model.PayoutAuditEvent;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PayoutAuditSubject {

    private final List<EntityObserver<PayoutAuditEvent>> observers = new ArrayList<>();

    public void registerObserver(EntityObserver<PayoutAuditEvent> observer) {
        observers.add(observer);
    }

    public void notifyObservers(PayoutAuditEvent event) {
        for (EntityObserver<PayoutAuditEvent> observer : observers) {
            observer.onEvent(event);
        }
    }
}
