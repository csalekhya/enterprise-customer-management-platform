package io.crmplatform.security;

import io.crmplatform.config.JwtProperties;
import io.crmplatform.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for token issuance and verification.
 *
 * <p>The clock is injected rather than mocked globally, which makes expiry a deterministic
 * assertion instead of a sleep.
 */
class JwtServiceTest {

    private static final Instant NOW = Instant.parse("2026-03-01T10:15:30Z");

    private final JwtProperties properties = new JwtProperties(
            "unit-test-signing-secret-of-sufficient-length-for-hs256",
            "slate-crm-test",
            Duration.ofMinutes(15),
            Duration.ofDays(7));

    private final JwtService jwtService = new JwtService(properties, Clock.fixed(NOW, ZoneOffset.UTC));

    private final CrmUserDetails principal = new CrmUserDetails(
            42L,
            "rep@slatecrm.io",
            "Marcus Ito",
            "irrelevant-for-token-issuance",
            true,
            Set.of(Role.Name.SALES_REP));

    @Test
    @DisplayName("access token carries subject, email, name and roles and expires in 15 minutes")
    void accessTokenCarriesIdentityClaims() {
        JwtService.IssuedToken issued = jwtService.issueAccessToken(principal);

        assertThat(issued.value()).isNotBlank();
        assertThat(issued.jti()).isNotBlank();
        assertThat(issued.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        assertThat(issued.expiresInSeconds()).isEqualTo(900L);

        Claims claims = jwtService.parse(issued.value(), JwtService.TYPE_ACCESS);
        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.getIssuer()).isEqualTo("slate-crm-test");
        assertThat(claims.get(JwtService.CLAIM_EMAIL, String.class)).isEqualTo("rep@slatecrm.io");
        assertThat(claims.get(JwtService.CLAIM_NAME, String.class)).isEqualTo("Marcus Ito");
        assertThat(claims.getId()).isEqualTo(issued.jti());
    }

    @Test
    @DisplayName("refresh token lives for 7 days and is typed separately")
    void refreshTokenUsesItsOwnTtlAndType() {
        JwtService.IssuedToken issued = jwtService.issueRefreshToken(principal);

        assertThat(issued.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        Claims claims = jwtService.parse(issued.value(), JwtService.TYPE_REFRESH);
        assertThat(claims.get(JwtService.CLAIM_TOKEN_TYPE, String.class)).isEqualTo(JwtService.TYPE_REFRESH);
    }

    @Test
    @DisplayName("an access token cannot be replayed where a refresh token is expected")
    void tokenTypesAreNotInterchangeable() {
        String accessToken = jwtService.issueAccessToken(principal).value();

        assertThatThrownBy(() -> jwtService.parse(accessToken, JwtService.TYPE_REFRESH))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("refresh");
    }

    @Test
    @DisplayName("parsing fails once the access token has expired")
    void expiredTokenIsRejected() {
        String token = jwtService.issueAccessToken(principal).value();

        JwtService laterService =
                new JwtService(properties, Clock.fixed(NOW.plus(Duration.ofMinutes(16)), ZoneOffset.UTC));

        assertThatThrownBy(() -> laterService.parse(token, JwtService.TYPE_ACCESS))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("a token signed with a different secret is rejected")
    void foreignSignatureIsRejected() {
        JwtProperties otherProperties = new JwtProperties(
                "a-completely-different-secret-value-also-long-enough",
                "slate-crm-test",
                Duration.ofMinutes(15),
                Duration.ofDays(7));
        String forged = new JwtService(otherProperties, Clock.fixed(NOW, ZoneOffset.UTC))
                .issueAccessToken(principal)
                .value();

        assertThatThrownBy(() -> jwtService.parse(forged, JwtService.TYPE_ACCESS)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("the principal rebuilt from claims keeps its authorities")
    void principalIsRebuiltFromClaims() {
        Claims claims = jwtService.parse(jwtService.issueAccessToken(principal).value(), JwtService.TYPE_ACCESS);

        CrmUserDetails restored = jwtService.toPrincipal(claims);

        assertThat(restored.id()).isEqualTo(42L);
        assertThat(restored.roles()).containsExactly(Role.Name.SALES_REP);
        assertThat(restored.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_SALES_REP");
        assertThat(restored.canBypassOwnership()).isFalse();
    }

    @Test
    @DisplayName("refresh tokens are digested to a stable 64 character hex string")
    void hashIsStableAndHexEncoded() {
        String token = jwtService.issueRefreshToken(principal).value();

        String first = jwtService.hash(token);
        String second = jwtService.hash(token);

        assertThat(first).isEqualTo(second).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(jwtService.hash(token + "x")).isNotEqualTo(first);
    }
}
