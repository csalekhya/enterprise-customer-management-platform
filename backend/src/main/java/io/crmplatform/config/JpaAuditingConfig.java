package io.crmplatform.config;

import io.crmplatform.security.CurrentUserProvider;
import io.crmplatform.security.CrmUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;

import java.util.Optional;

/**
 * Supplies the {@code createdBy} / {@code updatedBy} values for {@code BaseAuditEntity}.
 *
 * <p>Falls back to {@code system} for writes that happen outside a request - Flyway seeds and
 * scheduled maintenance - so the columns can stay meaningful without being nullable in practice.
 */
@Configuration
@RequiredArgsConstructor
public class JpaAuditingConfig {

    private static final String SYSTEM_PRINCIPAL = "system";

    private final CurrentUserProvider currentUserProvider;

    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> Optional.of(currentUserProvider
                .find()
                .map(CrmUserDetails::email)
                .orElse(SYSTEM_PRINCIPAL));
    }
}
