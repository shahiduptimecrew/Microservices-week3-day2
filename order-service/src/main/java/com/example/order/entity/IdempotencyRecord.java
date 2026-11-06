package com.example.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.time.Instant;

/**
 * Remembers each {@code Idempotency-Key} the client sent for {@code POST /api/v1/orders}.
 * <p>
 * The key is the primary key, so the database rejects a second insert of the same key
 * even when two identical requests arrive at the same instant. Stored in the database (not in
 * memory), so it works across restarts and across several Order Service instances.
 * <p>
 * Implements {@link Persistable} so that {@code save()} always INSERTs a new record instead of
 * silently UPDATE-ing an existing one (the default for entities with an assigned id).
 */
@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecord implements Persistable<String> {

    public enum Status { IN_PROGRESS, COMPLETED }

    @Id
    @Column(name = "idempotency_key", length = 100)
    private String key;

    /** SHA-256 of the request payload: the same key with a different body is rejected with 409. */
    @Column(nullable = false, length = 64)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    private Long orderId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Transient
    private boolean isNew = true;

    protected IdempotencyRecord() {
        // required by JPA
    }

    public IdempotencyRecord(String key, String requestHash) {
        this.key = key;
        this.requestHash = requestHash;
        this.status = Status.IN_PROGRESS;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        isNew = false;
    }

    public void complete(Long orderId) {
        this.orderId = orderId;
        this.status = Status.COMPLETED;
    }

    @Override
    public String getId() { return key; }

    @Override
    public boolean isNew() { return isNew; }

    public String getKey() { return key; }
    public String getRequestHash() { return requestHash; }
    public Status getStatus() { return status; }
    public Long getOrderId() { return orderId; }
    public Instant getCreatedAt() { return createdAt; }
}
