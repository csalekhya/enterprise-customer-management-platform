package io.crmplatform.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Audit columns shared by every business entity.
 *
 * <p>Using a {@code @MappedSuperclass} keeps the audit contract in one place while still letting
 * each table own its primary key strategy. The listener is attached here so callers never have to
 * remember to stamp {@code createdBy} / {@code updatedBy} by hand - values come from the
 * authenticated principal through {@code AuditorAware}.
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseAuditEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @CreatedBy
    @Column(name = "created_by", length = 180, updatable = false)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "updated_by", length = 180)
    private String updatedBy;
}
