package com.team26.freelance.contract.config;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.temporal.TemporalAccessor;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.stream.Collectors;

@Component("contractCacheKeys")
public class ContractCacheKeys {

    private static final String PREFIX = "contract-service::";

    public String contractDetail(Long id) {
        return PREFIX + "contract::" + id;
    }

    public String featureKey(String featureId, Object... params) {
        return PREFIX + featureId + "::" + hash(params);
    }

    private String hash(Object... params) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(canonicalParams(params).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private String canonicalParams(Object... params) {
        return Arrays.stream(params)
                .map(this::canonicalValue)
                .collect(Collectors.joining("|"));
    }

    private String canonicalValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        if (value instanceof TemporalAccessor) {
            return value.toString();
        }
        return String.valueOf(value);
    }
}
