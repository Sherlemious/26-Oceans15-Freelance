package com.team26.freelance.user.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class UserCacheKeys {

    public static final String USER_PREFIX = "user-service::user::";
    public static final String USER_SKILL_PREFIX = "user-service::user-skill::";
    public static final String FEATURE_PREFIX = "user-service::S1-F";
    public static final String FEATURE_PATTERN = "user-service::S1-F*::*";
    public static final String ACTIVITY_FEED_PATTERN = "user-service::S1-F12::*";
    public static final String LEGACY_USERS_ALL = "users:all";
    public static final String LEGACY_USER_PREFIX = "user:";
    public static final String LEGACY_USER_SKILL_PREFIX = "user-skills:";

    private UserCacheKeys() {
    }

    public static String user(Long id) {
        return USER_PREFIX + id;
    }

    public static String userSkill(Long id) {
        return USER_SKILL_PREFIX + id;
    }

    public static String userSearch(String name, String email, String role) {
        return featureHashKey("1", "name", name, "email", email, "role", role);
    }

    public static String contractSummary(Long id) {
        return featureIdKey("3", id);
    }

    public static String preferenceSearch(String key, String value) {
        return featureHashKey("5", "key", key, "value", value);
    }

    public static String topFreelancers(LocalDate startDate, LocalDate endDate, int limit) {
        return featureHashKey("6", "startDate", startDate, "endDate", endDate, "limit", limit);
    }

    public static String userProfile(Long id) {
        return featureIdKey("8", id);
    }

    public static String languagePreference(String lang, int minContracts) {
        return featureHashKey("9", "lang", lang, "minContracts", minContracts);
    }

    public static String activityFeed(Long id, int page, int size) {
        return FEATURE_PREFIX + "12::" + id + "::" + hash("page", page, "size", size);
    }

    public static String legacyUser(Long id) {
        return LEGACY_USER_PREFIX + id;
    }

    public static String legacyUserSkill(Long id) {
        return LEGACY_USER_SKILL_PREFIX + id;
    }

    static String hash(Object... values) {
        String payload = Stream.of(values)
                .map(UserCacheKeys::encode)
                .collect(Collectors.joining("|"));

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private static String featureHashKey(String feature, Object... values) {
        return FEATURE_PREFIX + feature + "::" + hash(values);
    }

    private static String featureIdKey(String feature, Long id) {
        return FEATURE_PREFIX + feature + "::" + id;
    }

    private static String encode(Object value) {
        if (value == null) {
            return "N:";
        }

        String text = value.toString();
        return "S" + text.length() + ":" + text;
    }
}
