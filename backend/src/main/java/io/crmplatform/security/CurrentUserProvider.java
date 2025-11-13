package io.crmplatform.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Single read point for "who is calling".
 *
 * <p>Wrapping {@link SecurityContextHolder} in an injectable bean keeps the static lookup out of
 * the service layer, which means ownership rules can be unit-tested with a stubbed principal
 * instead of a populated thread-local.
 */
@Component
public class CurrentUserProvider {

    public Optional<CrmUserDetails> find() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        return authentication.getPrincipal() instanceof CrmUserDetails principal
                ? Optional.of(principal)
                : Optional.empty();
    }

    /**
     * @throws UsernameNotFoundException when called outside an authenticated request; the security
     *     filter chain guarantees this cannot happen on a protected endpoint
     */
    public CrmUserDetails require() {
        return find().orElseThrow(() -> new UsernameNotFoundException("No authenticated principal on this request"));
    }

    public Long requireId() {
        return require().id();
    }
}
