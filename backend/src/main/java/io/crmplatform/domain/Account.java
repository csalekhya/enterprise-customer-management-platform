package io.crmplatform.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * A company the business sells to. Accounts are the aggregate root of the CRM: contacts,
 * opportunities and (transitively) interactions all hang off an account.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "accounts")
public class Account extends BaseAuditEntity {

    public enum Industry {
        TECHNOLOGY,
        FINANCIAL_SERVICES,
        HEALTHCARE,
        MANUFACTURING,
        RETAIL,
        LOGISTICS,
        ENERGY,
        PUBLIC_SECTOR,
        EDUCATION,
        OTHER
    }

    public enum Tier {
        BRONZE,
        SILVER,
        GOLD,
        PLATINUM
    }

    public enum Status {
        ACTIVE,
        PROSPECT,
        CHURNED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "industry", nullable = false, length = 40)
    private Industry industry;

    @Enumerated(EnumType.STRING)
    @Column(name = "tier", nullable = false, length = 20)
    private Tier tier;

    @Column(name = "annual_revenue", precision = 15, scale = 2)
    private BigDecimal annualRevenue;

    @Column(name = "website", length = 255)
    private String website;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    /** Lazy: list endpoints project the owner through a join fetch or a specification. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private User ownerUser;
}
