package com.team26.freelance.common.event;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

final class EventParams {

    private EventParams() {}

    static String stringValue(Map<String, Object> params, String key) {
        Object value = value(params, key);
        return value == null ? null : value.toString();
    }

    static Long longValue(Map<String, Object> params, String key) {
        Object value = value(params, key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Long.valueOf(text);
        }
        throw new IllegalArgumentException(key + " must be a number");
    }

    static Double doubleValue(Map<String, Object> params, String key) {
        Object value = value(params, key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Double.valueOf(text);
        }
        throw new IllegalArgumentException(key + " must be a number");
    }

    static LocalDateTime timestampValue(Map<String, Object> params) {
        Object value = value(params, "timestamp");
        if (value == null) {
            return LocalDateTime.now();
        }
        if (value instanceof LocalDateTime timestamp) {
            return timestamp;
        }
        if (value instanceof String text && !text.isBlank()) {
            return LocalDateTime.parse(text);
        }
        throw new IllegalArgumentException("timestamp must be a LocalDateTime");
    }

    static Map<String, Object> detailsValue(Map<String, Object> params) {
        Object value = value(params, "details");
        if (value == null) {
            return Map.of();
        }
        if (value instanceof Map<?, ?> rawDetails) {
            Map<String, Object> details = new LinkedHashMap<>();
            rawDetails.forEach((detailKey, detailValue) -> {
                if (detailKey != null) {
                    details.put(detailKey.toString(), detailValue);
                }
            });
            return details;
        }
        throw new IllegalArgumentException("details must be a map");
    }

    private static Object value(Map<String, Object> params, String key) {
        return params == null ? null : params.get(key);
    }
}
