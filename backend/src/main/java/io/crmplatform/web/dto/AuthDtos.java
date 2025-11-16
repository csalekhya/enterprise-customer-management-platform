package io.crmplatform.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Set;

/** Request and response payloads for the authentication endpoints. */
public final class AuthDtos {

    private AuthDtos() {}

    public record LoginRequest(
            @NotBlank @Email @Size(max = 180) String email,
            @NotBlank @Size(min = 8, max = 128) String password) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    public record LogoutRequest(@NotBlank String refreshToken) {}

    /**
     * @param accessToken  short-lived bearer token, held in browser memory only
     * @param refreshToken rotating token; the previous one is revoked server-side on every use
     * @param expiresIn    access-token lifetime in seconds, so the client can refresh proactively
     */
    public record TokenResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn,
            AuthenticatedUser user) {

        public static TokenResponse of(
                String accessToken, String refreshToken, long expiresIn, AuthenticatedUser user) {
            return new TokenResponse(accessToken, refreshToken, "Bearer", expiresIn, user);
        }
    }

    public record AuthenticatedUser(
            Long id, String email, String firstName, String lastName, Set<String> roles, Instant lastLoginAt) {}
}
