package com.noctivagous.ledgerpay.repository;

import com.noctivagous.ledgerpay.domain.LedgerEntry;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;


public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {
    
    List<LedgerEntry> findByAccountId(Long accountId);

    List<LedgerEntry> findByTransferId(UUID transferId);
}
