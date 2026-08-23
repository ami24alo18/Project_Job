package com.amit.jobagent.application;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "application_package_idempotency_alias")
class ApplicationPackageIdempotencyAlias {
    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "idempotency_key_hash", length = 64, columnDefinition = "char(64)")
    String idempotencyKeyHash;

    @Column(name = "application_package_revision_id", nullable = false, updatable = false)
    UUID revisionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;

    protected ApplicationPackageIdempotencyAlias() {}

    ApplicationPackageIdempotencyAlias(String idempotencyKeyHash, UUID revisionId) {
        this.idempotencyKeyHash = idempotencyKeyHash;
        this.revisionId = revisionId;
        this.createdAt = Instant.now();
    }
}
