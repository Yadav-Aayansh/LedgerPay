package com.noctivagous.ledgerpay.service;

import com.noctivagous.ledgerpay.AbstractIntegrationTest;
import com.noctivagous.ledgerpay.domain.Account;
import com.noctivagous.ledgerpay.domain.IdempotencyKey;
import com.noctivagous.ledgerpay.repository.AccountRepository;
import com.noctivagous.ledgerpay.repository.IdempotencyKeyRepository;
import com.noctivagous.ledgerpay.repository.LedgerEntryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TransferServiceTest extends AbstractIntegrationTest {

    @Autowired private TransferService transferService;
    @Autowired private AccountRepository accountRepository;
    @Autowired private LedgerEntryRepository ledgerEntryRepository;
    @Autowired private IdempotencyKeyRepository idempotencyKeyRepository;

    private Long accountAId;
    private Long accountBId;

    @BeforeEach
    void setup() {
        // real constructor: (ownerId, balance). ownerId is a dummy Long — no User entity yet.
        Account a = accountRepository.save(new Account(1L, new BigDecimal("1000.00")));
        Account b = accountRepository.save(new Account(2L, new BigDecimal("500.00")));
        accountAId = a.getId();
        accountBId = b.getId();
    }

    @AfterEach
    void cleanup() {
        ledgerEntryRepository.deleteAll();
        idempotencyKeyRepository.deleteAll();
        accountRepository.deleteAllById(List.of(accountAId, accountBId)); // keep the clearing account
    }

    @Test
    void singleThreaded_successfulTransfer() {
        String key = UUID.randomUUID().toString();

        UUID transferId = transferService.transfer(
                accountAId, accountBId, new BigDecimal("200.00"), key);

        assertNotNull(transferId);

        Account updatedA = accountRepository.findById(accountAId).orElseThrow();
        Account updatedB = accountRepository.findById(accountBId).orElseThrow();
        assertEquals(0, new BigDecimal("800.00").compareTo(updatedA.getBalance()));
        assertEquals(0, new BigDecimal("700.00").compareTo(updatedB.getBalance()));

        assertEquals(2, ledgerEntryRepository.findByTransferId(transferId).size());

        IdempotencyKey savedKey = idempotencyKeyRepository.findById(key).orElseThrow();
        assertEquals(IdempotencyKey.Status.SUCCEEDED, savedKey.getStatus());
    }
}