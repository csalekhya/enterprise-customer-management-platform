package io.crmplatform.service;

import io.crmplatform.config.CacheConfig;
import io.crmplatform.domain.Role;
import io.crmplatform.domain.User;
import io.crmplatform.exception.DuplicateResourceException;
import io.crmplatform.exception.ForbiddenOperationException;
import io.crmplatform.exception.ResourceNotFoundException;
import io.crmplatform.mapper.UserMapper;
import io.crmplatform.repository.RefreshTokenRepository;
import io.crmplatform.repository.RoleRepository;
import io.crmplatform.repository.UserRepository;
import io.crmplatform.security.CurrentUserProvider;
import io.crmplatform.web.dto.PageResponse;
import io.crmplatform.web.dto.UserDtos;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** User administration. Every method here is reachable only by ADMIN. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserProvider currentUserProvider;

    public PageResponse<UserDtos.Response> list(String search, Pageable pageable) {
        Page<User> page = userRepository.search(StringUtils.hasText(search) ? search.trim() : null, pageable);
        return PageResponse.from(page, userMapper::toResponse);
    }

    /**
     * Owner picker options.
     *
     * <p>Cached as reference data: the dropdown is rendered on every account, contact and deal form,
     * and the user directory of a sales organisation changes a few times a quarter.
     */
    @Cacheable(cacheNames = CacheConfig.REFERENCE_DATA, key = "'user-options'")
    public List<UserDtos.Option> options() {
        return userRepository.findAll().stream()
                .filter(User::isEnabled)
                .sorted((left, right) -> left.fullName().compareToIgnoreCase(right.fullName()))
                .map(userMapper::toOption)
                .toList();
    }

    public UserDtos.Response get(Long id) {
        return userMapper.toResponse(loadUser(id));
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.REFERENCE_DATA, allEntries = true)
    public UserDtos.Response create(UserDtos.CreateRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new DuplicateResourceException("A user with email %s already exists".formatted(request.email()));
        }

        User user = new User();
        user.setEmail(request.email().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setEnabled(true);
        user.setRoles(resolveRoles(request.roles()));

        User saved = userRepository.save(user);
        log.info("User {} created with roles {}", saved.getEmail(), request.roles());
        return userMapper.toResponse(saved);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.REFERENCE_DATA, allEntries = true)
    public UserDtos.Response update(Long id, UserDtos.UpdateRequest request) {
        User user = loadUser(id);
        Long callerId = currentUserProvider.requireId();

        boolean losingAdmin = user.hasRole(Role.Name.ADMIN) && !request.roles().contains(Role.Name.ADMIN);
        if (id.equals(callerId) && (Boolean.FALSE.equals(request.enabled()) || losingAdmin)) {
            // Guard against the classic self-lockout: the last admin demoting or disabling
            // themselves leaves the installation unadministrable.
            throw new ForbiddenOperationException("You cannot remove your own administrator access");
        }

        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setEnabled(Boolean.TRUE.equals(request.enabled()));
        user.setRoles(resolveRoles(request.roles()));

        // Authorities are baked into issued access tokens, so a role change must invalidate the
        // refresh chain; the next silent refresh fails and the client re-authenticates.
        int revoked = refreshTokenRepository.revokeAllForUser(id);
        log.info("User {} updated; {} refresh token(s) revoked", user.getEmail(), revoked);

        return userMapper.toResponse(user);
    }

    private Set<Role> resolveRoles(Set<Role.Name> names) {
        List<Role> found = roleRepository.findByNameIn(names);
        if (found.size() != names.size()) {
            throw new ResourceNotFoundException("One or more roles do not exist: " + names);
        }
        return new LinkedHashSet<>(found);
    }

    private User loadUser(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
    }
}
