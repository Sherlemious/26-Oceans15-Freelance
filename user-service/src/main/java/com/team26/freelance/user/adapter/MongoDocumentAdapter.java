package com.team26.freelance.user.adapter;

import com.team26.freelance.user.dto.AuthEventDTO;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bson.Document;
import org.springframework.stereotype.Component;

@Component
public class MongoDocumentAdapter {

    public AuthEventDTO adapt(Document document) {
        if (document == null) {
            return new AuthEventDTO(null, null, Map.of());
        }

        return new AuthEventDTO(
                document.getString("action"),
                toLocalDateTime(document.get("timestamp")),
                toDetails(document.get("details"))
        );
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime timestamp) {
            return timestamp;
        }
        if (value instanceof java.util.Date date) {
            Instant instant = date.toInstant();
            return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        }
        return LocalDateTime.parse(value.toString());
    }

    private Map<String, Object> toDetails(Object value) {
        if (!(value instanceof Map<?, ?> rawDetails)) {
            return Map.of();
        }

        Map<String, Object> details = new LinkedHashMap<>();
        rawDetails.forEach((key, detailValue) -> {
            if (key != null) {
                details.put(key.toString(), detailValue);
            }
        });
        return details;
    }
}
