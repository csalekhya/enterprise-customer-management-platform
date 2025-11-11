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
import java.time.LocalDate;

/** A revenue deal against an {@link Account}; the unit of the sales pipeline. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "opportunities")
public class Opportunity extends BaseAuditEntity {

    public enum Stage {
        DISCOVERY,
        PROPOSAL,
        NEGOTIATION,
        WON,
        LOST;

        /** Open stages are the ones that make up forecastable pipeline value. */
        public boolean isOpen() {
            return this != WON && this != LOST;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false, length = 20)
    private Stage stage;

    @Column(name = "probability", nullable = false)
    private int probability;

    @Column(name = "expected_close_date")
    private LocalDate expectedCloseDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private User ownerUser;

    /** Amount weighted by win probability - what a forecast actually reports. */
    public BigDecimal weightedAmount() {
        return amount.multiply(BigDecimal.valueOf(probability)).movePointLeft(2);
    }
}
