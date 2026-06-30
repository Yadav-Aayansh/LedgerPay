package com.noctivagous.ledgerpay.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;

@Entity
@Table(name = "ledger_entry", indexes = {
    @Index(name = "idx_ledger_account", columnList = "account_id"),
    @Index(name = "idx_ledger_transfer", columnList = "transfer_id")
})
public class LedgerEntry {
    
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ledgerEntriesGenerator")
    @SequenceGenerator(name = "ledgerEntriesGenerator", sequenceName = "ledger_entry_seq", allocationSize = 50)
    private Long id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private Long accountId;

    @Column(nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal amount;

    @Column(name = "transfer_id", nullable = false, updatable = false)
    private UUID transferId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerEntry() {}

    public LedgerEntry(Long accountId, BigDecimal amount, UUID transferId) {
        this.accountId = accountId;
        this.amount = amount;
        this.transferId = transferId;
    }

    public Long getId() { return id; }
    public Long getAccountId() { return accountId; }
    public BigDecimal getAmount() { return amount; }
    public UUID getTransferId() { return transferId; }
    public Instant getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LedgerEntry other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
