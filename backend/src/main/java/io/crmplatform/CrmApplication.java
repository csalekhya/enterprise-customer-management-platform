package io.crmplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Entry point for the Enterprise Customer Management Platform API.
 *
 * <p>JPA auditing is enabled here (rather than on a nested configuration class) so that the
 * {@code auditorAware} bean declared in {@code config.JpaAuditingConfig} is bound to every
 * {@code BaseAuditEntity} write without additional wiring.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
@EnableCaching
public class CrmApplication {

    public static void main(String[] args) {
        SpringApplication.run(CrmApplication.class, args);
    }
}
