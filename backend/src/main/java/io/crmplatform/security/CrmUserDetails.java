package io.crmplatform.security;

import io.crmplatform.domain.Role;
import io.crmplatform.domain.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The authenticated principal.
 *
 * <p>Carrying the numeric user id (not just the email) is what makes ownership checks in the
 * service layer a field comparison instead of an extra {@code users} lookup on every write.
 */
public record CrmUserDetails(
        Long id,
        String email,
        String fullName,
        String passwordHash,
        boolean active,
        Set<Role.Name> roles)
        implements UserDetails {

    public static CrmUserDetails from(User user) {
        return new CrmUserDetails(
                user.getId(),
                user.getEmail(),
                user.fullName(),
                user.getPasswordHash(),
                user.isEnabled(),
                user.getRoles().stream().map(Role::getName).collect(Collectors.toUnmodifiableSet()));
    }

    /** Rebuilt from JWT claims on stateless requests, where no database round trip happens. */
    public static CrmUserDetails fromClaims(Long id, String email, String fullName, Set<Role.Name> roles) {
        return new CrmUserDetails(id, email, fullName, "", true, roles);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return active;
    }

    @Override
    public boolean isAccountNonLocked() {
        return active;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return active;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    public boolean hasAnyRole(Role.Name... candidates) {
        return List.of(candidates).stream().anyMatch(roles::contains);
    }

    /** True when the caller may act on records they do not own. */
    public boolean canBypassOwnership() {
        return hasAnyRole(Role.Name.ADMIN, Role.Name.SALES_MANAGER);
    }
}
