package io.crmplatform.security;

import io.crmplatform.config.JwtProperties;
import io.crmplatform.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Issues and verifies the platform's HS256 tokens.
 *
 * <p>Two token kinds are issued from the same key but are not interchangeable: the {@code typ}
 * claim is asserted on parse, so an access token can never be replayed against
 * {@code /auth/refresh} and a refresh token can never be presented as a bearer credential.
 */
@Slf4j
@Service
public class JwtService {

    public static final String CLAIM_TOKEN_TYPE = "typ";
    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_NAME = "name";
    public static final String CLAIM_ROLES = "roles";

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final JwtProperties properties;
    private final SecretKey signingKey;
    private final Clock clock;

    /**
     * The constructor Spring uses. With two public constructors and no marker, Spring falls back
     * to a no-arg constructor that does not exist, so this one has to be chosen explicitly.
     */
    @Autowired
    public JwtService(JwtProperties properties) {
        this(properties, Clock.systemUTC());
    }

    /** Clock-injecting constructor: expiry behaviour is unit-testable without sleeping. */
    public JwtService(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    /** A freshly minted token plus the metadata the caller needs to persist or return it. */
    public record IssuedToken(String value, String jti, Instant issuedAt, Instant expiresAt) {

        public long expiresInSeconds() {
            return expiresAt.getEpochSecond() - issuedAt.getEpochSecond();
        }
    }

    public IssuedToken issueAccessToken(CrmUserDetails principal) {
        return issue(principal, TYPE_ACCESS, properties.accessTokenTtl());
    }

    public IssuedToken issueRefreshToken(CrmUserDetails principal) {
        return issue(principal, TYPE_REFRESH, properties.refreshTokenTtl());
    }

    private IssuedToken issue(CrmUserDetails principal, String tokenType, java.time.Duration ttl) {
        Instant now = clock.instant();
        Instant expiry = now.plus(ttl);
        String jti = UUID.randomUUID().toString();

        String token = Jwts.builder()
                .id(jti)
                .issuer(properties.issuer())
                .subject(String.valueOf(principal.id()))
                .claim(CLAIM_EMAIL, principal.email())
                .claim(CLAIM_NAME, principal.fullName())
                .claim(CLAIM_ROLES, principal.roles().stream().map(Enum::name).sorted().toList())
                .claim(CLAIM_TOKEN_TYPE, tokenType)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();

        return new IssuedToken(token, jti, now, expiry);
    }

    /**
     * Verifies signature, issuer, expiry and token type.
     *
     * @throws JwtException when the token is unusable for the requested purpose
     */
    public Claims parse(String token, String expectedType) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(properties.issuer())
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String actualType = claims.get(CLAIM_TOKEN_TYPE, String.class);
        if (!expectedType.equals(actualType)) {
            throw new JwtException("Expected a '%s' token but received '%s'".formatted(expectedType, actualType));
        }
        return claims;
    }

    /** Rebuilds the principal from an access token without touching the database. */
    public CrmUserDetails toPrincipal(Claims claims) {
        Set<Role.Name> roles = readRoles(claims);
        return CrmUserDetails.fromClaims(
                Long.valueOf(claims.getSubject()),
                claims.get(CLAIM_EMAIL, String.class),
                claims.get(CLAIM_NAME, String.class),
                roles);
    }

    private Set<Role.Name> readRoles(Claims claims) {
        Object raw = claims.get(CLAIM_ROLES);
        if (!(raw instanceof List<?> values)) {
            return Set.of();
        }
        return values.stream()
                .map(String::valueOf)
                .map(this::toRoleName)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
    }

    private Role.Name toRoleName(String value) {
        try {
            return Role.Name.valueOf(value);
        } catch (IllegalArgumentException ex) {
            // A role that was removed from the enum after a token was issued: ignore it rather
            // than rejecting the whole request, so a deploy cannot log every user out.
            log.debug("Ignoring unknown role claim '{}'", value);
            return null;
        }
    }

    /**
     * SHA-256 of the raw token, hex encoded to exactly 64 characters.
     *
     * <p>Refresh tokens are stored only as this digest. BCrypt would be wrong here: the value is
     * already high-entropy, so a slow KDF buys nothing and would cost a hash per refresh.
     */
    public String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required by the JDK specification", ex);
        }
    }

    public Instant now() {
        return clock.instant();
    }
}
