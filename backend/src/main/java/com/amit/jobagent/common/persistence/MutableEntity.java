package com.amit.jobagent.common.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@MappedSuperclass
public abstract class MutableEntity {
    @Id protected UUID id;
    @Version @Column(name = "record_version", nullable = false) protected long recordVersion;
    @Column(name = "created_at", nullable = false, updatable = false) protected Instant createdAt;
    @Column(name = "updated_at", nullable = false) protected Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = Instant.now();
        updatedAt = createdAt;
    }
    @PreUpdate protected void onUpdate() { updatedAt = Instant.now(); }
    public UUID getId() { return id; }
    public long getRecordVersion() { return recordVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    protected void touch() { updatedAt = Instant.now(); }
}
