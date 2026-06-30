package com.noctivagous.ledgerpay.domain;

import jakarta.persistence.*;

import java.util.UUID;
import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "idempotency_key")
public class IdempotencyKey {
    public enum Status { PENDING, SUCCEEDED, FAILED }

    @Id
    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private String key;

    @Column(name = "transfer_id")
    private UUID transferId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected IdempotencyKey() {}

    public IdempotencyKey(String key) {
        this.key = key;
        this.status = Status.PENDING;
    }

    public String getKey() { return key; }
    public UUID getTransferId() { return transferId; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }

    public void setStatus(Status status) { this.status = status; }
    public void setTransferId(UUID transferId) { this.transferId = transferId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IdempotencyKey other)) return false;
        return key != null && key.equals(other.key);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
