package com.team26.freelance.wallet.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WalletJwtServiceTest {

    private static final String SECRET = "test-secret";
    private static final String ISSUER = "freelance-platform";
    private static final String AUDIENCE = "wallet-service";
    private final WalletJwtService walletJwtService = new WalletJwtService(SECRET, ISSUER, AUDIENCE, new ObjectMapper());

    @Test
    void validateAuthorizationHeaderShouldAcceptValidHs256Jwt() {
        String token = tokenWithExp(Instant.now().plusSeconds(60).getEpochSecond());

        assertDoesNotThrow(() -> walletJwtService.validateAuthorizationHeader("Bearer " + token));
    }

    @Test
    void validateAuthorizationHeaderShouldRejectInvalidSignature() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> walletJwtService.validateAuthorizationHeader("Bearer " + tokenWithExp(Instant.now().plusSeconds(60).getEpochSecond()) + "tampered"));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
    }

    @Test
    void validateAuthorizationHeaderShouldRejectExpiredToken() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> walletJwtService.validateAuthorizationHeader("Bearer " + tokenWithExp(Instant.now().minusSeconds(60).getEpochSecond())));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
    }

    @Test
    void validateAuthorizationHeaderShouldRejectMissingExpiration() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> walletJwtService.validateAuthorizationHeader("Bearer " + tokenWithPayload("{\"sub\":\"55-1744\",\"iss\":\"" + ISSUER + "\",\"aud\":\"" + AUDIENCE + "\"}")));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
    }

    @Test
    void validateAuthorizationHeaderShouldRejectWrongIssuer() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> walletJwtService.validateAuthorizationHeader("Bearer " + tokenWithClaims(Instant.now().plusSeconds(60).getEpochSecond(), "other", AUDIENCE)));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
    }

    @Test
    void validateAuthorizationHeaderShouldRejectWrongAudience() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> walletJwtService.validateAuthorizationHeader("Bearer " + tokenWithClaims(Instant.now().plusSeconds(60).getEpochSecond(), ISSUER, "other-service")));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
    }

    private String tokenWithExp(long exp) {
        return tokenWithClaims(exp, ISSUER, AUDIENCE);
    }

    private String tokenWithClaims(long exp, String issuer, String audience) {
        return tokenWithPayload("{\"sub\":\"55-1744\",\"exp\":" + exp + ",\"iss\":\"" + issuer + "\",\"aud\":\"" + audience + "\"}");
    }

    private String tokenWithPayload(String payloadJson) {
        String header = encode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = encode(payloadJson);
        String unsignedToken = header + "." + payload;
        return unsignedToken + "." + sign(unsignedToken);
    }

    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
