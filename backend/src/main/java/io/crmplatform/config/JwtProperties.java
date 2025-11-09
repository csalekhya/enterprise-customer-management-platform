package io.crmplatform.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Token settings, bound from {@code crm.security.jwt.*}.
 *
 * <p>The secret is validated for length at startup: an HS256 key shorter than 32 bytes is rejected
 * by the JJWT key factory anyway, and failing at boot beats failing on the first login.
 *
 * @param secret            HMAC-SHA key material, injected from Secrets Manager in AWS
 * @param issuer            {@code iss} claim, asserted on every parse
 * @param accessTokenTtl    lifetime of the bearer token held in browser memory
 * @param refreshTokenTtl   lifetime of the rotating refresh token
 */
@Validated
@ConfigurationProperties(prefix = "crm.security.jwt")
public record JwtProperties(
        @NotBlank @Size(min = 32, message = "JWT secret must be at least 32 characters for HS256") String secret,
        @NotBlank String issuer,
        Duration accessTokenTtl,
        Duration refreshTokenTtl) {

    public JwtProperties {
        if (accessTokenTtl == null) {
            accessTokenTtl = Duration.ofMinutes(15);
        }
        if (refreshTokenTtl == null) {
            refreshTokenTtl = Duration.ofDays(7);
        }
    }
}
