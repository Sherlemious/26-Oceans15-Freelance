package com.team26.freelance.user.logging;

import org.slf4j.MDC;

public final class MdcUserScope implements AutoCloseable {

    private static final String USER_ID_KEY = "userId";

    private final String previousUserId;

    private MdcUserScope(String previousUserId) {
        this.previousUserId = previousUserId;
    }

    public static MdcUserScope put(Long userId) {
        String previousUserId = MDC.get(USER_ID_KEY);
        if (userId == null) {
            MDC.remove(USER_ID_KEY);
        } else {
            MDC.put(USER_ID_KEY, String.valueOf(userId));
        }
        return new MdcUserScope(previousUserId);
    }

    @Override
    public void close() {
        if (previousUserId == null) {
            MDC.remove(USER_ID_KEY);
        } else {
            MDC.put(USER_ID_KEY, previousUserId);
        }
    }
}
