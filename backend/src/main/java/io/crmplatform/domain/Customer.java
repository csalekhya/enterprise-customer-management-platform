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

/**
 * A human contact inside an {@link Account}. "Customer" is the CRM term for the person, not the
 * paying company - the lifecycle stage tracks how far along the funnel the relationship is.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "customers")
public class Customer extends BaseAuditEntity {

    public enum LifecycleStage {
        LEAD,
        QUALIFIED,
        CUSTOMER,
        CHAMPION
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "first_name", nullable = false, length = 80)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 80)
    private String lastName;

    @Column(name = "email", nullable = false, length = 180)
    private String email;

    @Column(name = "phone", length = 32)
    private String phone;

    @Column(name = "job_title", length = 120)
    private String jobTitle;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_stage", nullable = false, length = 20)
    private LifecycleStage lifecycleStage;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private User ownerUser;

    /**
     * Comma separated free-form labels. Deliberately denormalised: tags are only ever read back as
     * a whole and never joined on, so a child table would add a fetch for no query benefit.
     */
    @Column(name = "tags", length = 255)
    private String tags;

    public String fullName() {
        return firstName + " " + lastName;
    }
}
