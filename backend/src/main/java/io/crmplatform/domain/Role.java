package io.crmplatform.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A grantable authority. Roles are reference data seeded by Flyway and never created at runtime,
 * which keeps the authorization matrix reviewable in version control.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "roles")
public class Role {

    /** The four roles the platform ships with. Prefixed with {@code ROLE_} by Spring Security. */
    public enum Name {
        /** Unrestricted access, including user administration. */
        ADMIN,
        /** Read and write on every record plus the ability to reassign ownership. */
        SALES_MANAGER,
        /** Read everything, write only records they own. */
        SALES_REP,
        /** Read-only analyst / auditor access. */
        READ_ONLY
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "name", nullable = false, length = 40, unique = true)
    private Name name;

    public String authority() {
        return "ROLE_" + name.name();
    }
}
