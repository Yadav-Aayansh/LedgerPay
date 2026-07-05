package com.noctivagous.ledgerpay.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.noctivagous.ledgerpay.domain.LedgerEntry;

public record LedgerEntryResponse(Long id, UUID transferId, BigDecimal amount, Instant createdAt) {

    public static LedgerEntryResponse from(LedgerEntry entry) {
        return new LedgerEntryResponse(entry.getId(), entry.getTransferId(), entry.getAmount(), entry.getCreatedAt());
    }
}
