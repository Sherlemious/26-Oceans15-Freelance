package com.team26.freelance.common.event;

import java.util.Locale;
import java.util.Optional;

public enum ObservabilityAction {
    ANALYTICS_VIEWED,
    DASHBOARD_VIEWED,
    MILESTONE_TRACKED;

    public static Optional<ObservabilityAction> from(String action) {
        if (action == null || action.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(ObservabilityAction.valueOf(action.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public static boolean isPureObservabilityAction(String action) {
        return from(action).isPresent();
    }
}
