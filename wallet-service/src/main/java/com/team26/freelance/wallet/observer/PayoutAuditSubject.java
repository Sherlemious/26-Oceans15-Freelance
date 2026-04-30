package com.team26.freelance.wallet.observer;

import com.team26.freelance.wallet.model.PayoutAuditEvent;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PayoutAuditSubject {

    private final List<EntityObserver<PayoutAuditEvent>> observers;

    public PayoutAuditSubject(List<EntityObserver<PayoutAuditEvent>> observers) {
        this.observers = List.copyOf(observers);
    }

    public void notifyObservers(PayoutAuditEvent event) {
        observers.forEach(observer -> observer.onEvent(event));
    }
}
