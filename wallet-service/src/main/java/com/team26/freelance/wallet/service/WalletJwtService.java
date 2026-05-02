package com.team26.freelance.wallet.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WalletJwtService {

    private final String jwtSecret;
    private final String expectedIssuer;
    private final String expectedAudience;
    private final ObjectMapper objectMapper;

    public WalletJwtService(@Value("${wallet.jwt.secret}") String jwtSecret,
                            @Value("${wallet.jwt.issuer}") String expectedIssuer,
                            @Value("${wallet.jwt.audience}") String expectedAudience,
                            ObjectMapper objectMapper) {
        this.jwtSecret = jwtSecret;
        this.expectedIssuer = expectedIssuer;
        this.expectedAudience = expectedAudience;
        this.objectMapper = objectMapper;
    }

    public void validateAuthorizationHeader(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bearer token is required");
        }
        validateToken(authorization.substring("Bearer ".length()).trim());
    }

    private void validateToken(String token) {
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3) {
                throw unauthorized();
            }

            Map<String, Object> header = readJsonPart(parts[0]);
            if (!"HS256".equals(header.get("alg"))) {
                throw unauthorized();
            }

            String unsignedToken = parts[0] + "." + parts[1];
            String expectedSignature = sign(unsignedToken);
            if (!constantTimeEquals(expectedSignature, parts[2])) {
                throw unauthorized();
            }

            Map<String, Object> claims = readJsonPart(parts[1]);
            Object exp = claims.get("exp");
            if (!(exp instanceof Number number) || number.longValue() < Instant.now().getEpochSecond()) {
                throw unauthorized();
            }
            if (!expectedIssuer.equals(claims.get("iss")) || !hasExpectedAudience(claims.get("aud"))) {
                throw unauthorized();
            }
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw unauthorized();
        }
    }

    private Map<String, Object> readJsonPart(String encoded) {
        try {
            byte[] json = Base64.getUrlDecoder().decode(encoded);
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            throw unauthorized();
        }
    }

    private boolean hasExpectedAudience(Object aud) {
        if (aud instanceof String audience) {
            return expectedAudience.equals(audience);
        }
        if (aud instanceof List<?> audiences) {
            return audiences.stream().anyMatch(expectedAudience::equals);
        }
        return false;
    }

    private String sign(String unsignedToken) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signature = mac.doFinal(unsignedToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        } catch (Exception ex) {
            throw unauthorized();
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = actual.getBytes(StandardCharsets.UTF_8);
        if (expectedBytes.length != actualBytes.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < expectedBytes.length; i++) {
            result |= expectedBytes[i] ^ actualBytes[i];
        }
        return result == 0;
    }

    private ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid bearer token");
    }
}
