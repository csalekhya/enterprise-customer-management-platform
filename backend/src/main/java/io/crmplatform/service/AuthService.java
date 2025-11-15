package io.crmplatform.service;

import io.crmplatform.domain.RefreshToken;
import io.crmplatform.domain.Role;
import io.crmplatform.domain.User;
import io.crmplatform.exception.ResourceNotFoundException;
import io.crmplatform.repository.RefreshTokenRepository;
import io.crmplatform.repository.UserRepository;
import io.crmplatform.security.CrmUserDetails;
import io.crmplatform.security.JwtService;
import io.crmplatform.web.dto.AuthDtos;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Authentication use cases: password grant, refresh rotation and revocation.
 *
 * <p><b>Why two tokens.</b> The access token is a self-contained 15 minute bearer credential, so
 * authorizing a request needs no database round trip at all - which is what lets the API scale
 * horizontally without a shared session store. The cost of that statelessness is that an access
 * token cannot be recalled, so it is kept deliberately short-lived and never persisted by the
 * client beyond memory.
 *
 * <p><b>Why rotation.</b> The refresh token is the long-lived half, so it is the one worth
 * stealing. Every successful refresh revokes the presented token and issues a new one, which means
 * a stolen token works at most once and its reuse is a detectable event rather than a silent
 * week-long compromise. Only the SHA-256 digest is stored.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;

    @Transactional
    public AuthDtos.TokenResponse login(AuthDtos.LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        CrmUserDetails principal = (CrmUserDetails) authentication.getPrincipal();
        User user = userRepository
                .findByEmailIgnoreCase(principal.email())
                .orElseThrow(() -> new ResourceNotFoundException("User", principal.email()));

        user.setLastLoginAt(jwtService.now());
        log.info("Successful login for {}", user.getEmail());

        return issuePair(principal, user);
    }

    /**
     * Exchanges a refresh token for a new pair, revoking the presented one.
     *
     * <p>Reuse of an already-revoked token is treated as a compromise signal: the whole chain for
     * that user is revoked, forcing a fresh password login everywhere.
     *
     * <p>{@code noRollbackFor} matters here: the replay branch writes the revocation and then
     * throws to produce the 401. With the default rollback rule that exception would undo the
     * revocation, and the newer token in the chain would keep working.
     */
    @Transactional(noRollbackFor = BadCredentialsException.class)
    public AuthDtos.TokenResponse refresh(String presentedToken) {
        Claims claims;
        try {
            claims = jwtService.parse(presentedToken, JwtService.TYPE_REFRESH);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BadCredentialsException("Refresh token is invalid or expired");
        }

        String hash = jwtService.hash(presentedToken);
        RefreshToken stored = refreshTokenRepository
                .findByTokenHash(hash)
                .orElseThrow(() -> new BadCredentialsException("Refresh token is not recognised"));

        if (!stored.isUsable(jwtService.now())) {
            int revoked = refreshTokenRepository.revokeAllForUser(stored.getUser().getId());
            log.warn(
                    "Replay of a revoked or expired refresh token for user {}; revoked {} active token(s)",
                    stored.getUser().getId(),
                    revoked);
            throw new BadCredentialsException("Refresh token is no longer valid");
        }

        User user = stored.getUser();
        if (!user.isEnabled()) {
            throw new BadCredentialsException("Account is disabled");
        }
        if (!String.valueOf(user.getId()).equals(claims.getSubject())) {
            throw new BadCredentialsException("Refresh token does not match its subject");
        }

        stored.setRevoked(true);
        return issuePair(CrmUserDetails.from(user), user);
    }

    /** Idempotent: an unknown or already-revoked token is still a successful logout. */
    @Transactional
    public void logout(String presentedToken) {
        refreshTokenRepository
                .findByTokenHash(jwtService.hash(presentedToken))
                .ifPresent(token -> token.setRevoked(true));
    }

    @Transactional(readOnly = true)
    public AuthDtos.AuthenticatedUser currentUser(CrmUserDetails principal) {
        User user = userRepository
                .findByEmailIgnoreCase(principal.email())
                .orElseThrow(() -> new ResourceNotFoundException("User", principal.email()));
        return toAuthenticatedUser(user);
    }

    private AuthDtos.TokenResponse issuePair(CrmUserDetails principal, User user) {
        JwtService.IssuedToken access = jwtService.issueAccessToken(principal);
        JwtService.IssuedToken refresh = jwtService.issueRefreshToken(principal);

        RefreshToken record = new RefreshToken();
        record.setUser(user);
        record.setTokenHash(jwtService.hash(refresh.value()));
        record.setExpiresAt(refresh.expiresAt());
        record.setRevoked(false);
        record.setCreatedAt(Instant.now());
        refreshTokenRepository.save(record);

        return AuthDtos.TokenResponse.of(
                access.value(), refresh.value(), access.expiresInSeconds(), toAuthenticatedUser(user));
    }

    private AuthDtos.AuthenticatedUser toAuthenticatedUser(User user) {
        Set<String> roles =
                user.getRoles().stream().map(Role::getName).map(Enum::name).collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        return new AuthDtos.AuthenticatedUser(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                roles,
                user.getLastLoginAt());
    }
}
