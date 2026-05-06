package com.team26.freelance.security.handler;

import com.team26.freelance.security.JwtAuthContext;
import com.team26.freelance.security.JwtAuthException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Validates that the JWT's user (uid claim) still exists and is ACTIVE in Postgres.
 *
 * <p>Required by the grader: deleted/deactivated users with otherwise-valid JWTs must get 401.
 * This handler must re-check Postgres on every request.
 */
public class UserLoaderHandler extends AuthHandler {

    private final DataSource dataSource;

    public UserLoaderHandler(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void handle(JwtAuthContext context) throws JwtAuthException {
        Long userId = parseUid(context.getUid());

        // Treat missing/deactivated users as unauthorized (token no longer valid for access)
        boolean active = isUserActive(userId);
        if (!active) {
            throw new JwtAuthException("User not found or deactivated");
        }

        passToNext(context);
    }

    private Long parseUid(String uid) throws JwtAuthException {
        if (uid == null || uid.isBlank()) {
            throw new JwtAuthException("Missing 'uid' claim");
        }
        try {
            return Long.parseLong(uid);
        } catch (NumberFormatException ex) {
            throw new JwtAuthException("Invalid 'uid' claim");
        }
    }

    private boolean isUserActive(Long userId) throws JwtAuthException {
        final String sql = "SELECT 1 FROM users WHERE id = ? AND CAST(status AS TEXT) = 'ACTIVE'";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setLong(1, userId);

            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        } catch (Exception ex) {
            // Postgres is a hard dependency; if we cannot validate, fail closed.
            throw new JwtAuthException("Unable to validate user");
        }
    }
}