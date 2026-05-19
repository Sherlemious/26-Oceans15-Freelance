package com.team26.freelance.job.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.LayoutBase;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public class JobServiceJsonLogLayout extends LayoutBase<ILoggingEvent> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String SERVICE_NAME = "job-service";

    @Override
    public String doLayout(ILoggingEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("timestamp", Instant.ofEpochMilli(event.getTimeStamp()).toString());
        payload.put("service", SERVICE_NAME);
        payload.put("level", event.getLevel().toString());
        payload.put("thread", event.getThreadName());
        payload.put("logger", event.getLoggerName());
        payload.put("correlationId", mdc(event, "correlationId"));
        payload.put("jobId", mdc(event, "jobId"));
        payload.put("proposalId", mdc(event, "proposalId"));
        payload.put("routingKey", mdc(event, "routingKey"));
        payload.put("message", event.getFormattedMessage());

        IThrowableProxy throwable = event.getThrowableProxy();
        if (throwable != null) {
            payload.put("exception", throwable.getClassName());
            payload.put("exceptionMessage", throwable.getMessage());
            payload.put("stackTrace", stackTrace(throwable));
        }

        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return "{\"service\":\"job-service\",\"level\":\"ERROR\",\"message\":\"failed to encode log event\"}";
        }
    }

    private String mdc(ILoggingEvent event, String key) {
        String value = event.getMDCPropertyMap().get(key);
        return value == null ? "" : value;
    }

    private String stackTrace(IThrowableProxy throwable) {
        StringBuilder builder = new StringBuilder();
        appendStackTrace(builder, throwable, "");
        return builder.toString();
    }

    private void appendStackTrace(StringBuilder builder, IThrowableProxy throwable, String prefix) {
        builder.append(prefix)
                .append(throwable.getClassName())
                .append(": ")
                .append(throwable.getMessage() == null ? "" : throwable.getMessage());

        for (StackTraceElementProxy element : throwable.getStackTraceElementProxyArray()) {
            builder.append("\\n    at ").append(element.getSTEAsString());
        }

        IThrowableProxy cause = throwable.getCause();
        if (cause != null) {
            builder.append("\\nCaused by: ");
            appendStackTrace(builder, cause, "");
        }
    }
}
