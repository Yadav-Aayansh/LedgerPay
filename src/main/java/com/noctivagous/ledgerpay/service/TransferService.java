package com.noctivagous.ledgerpay.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.noctivagous.ledgerpay.domain.Account;
import com.noctivagous.ledgerpay.domain.IdempotencyKey;
import com.noctivagous.ledgerpay.domain.LedgerEntry;
import com.noctivagous.ledgerpay.repository.AccountRepository;
import com.noctivagous.ledgerpay.repository.IdempotencyKeyRepository;
import com.noctivagous.ledgerpay.repository.LedgerEntryRepository;


@Service
public class TransferService {
    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;


    public TransferService(AccountRepository accountRepository, LedgerEntryRepository ledgerEntryRepository, IdempotencyKeyRepository idempotencyKeyRepository) {
        this.accountRepository = accountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
    }

    @Transactional
    public UUID transfer(Long fromAccountId, Long toAccountId, BigDecimal amount, String idempotencyKey) {
        // 1. Validation error
        if (fromAccountId.equals(toAccountId)) {
            throw new IllegalArgumentException("Cannot transfer money to the same account");
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transfer amount must be greater than zero");
        }

        // 2. Idempotency Check
        var existing = idempotencyKeyRepository.findById(idempotencyKey);
        if (existing.isPresent()) {
            IdempotencyKey existingkey = existing.get();
            if (existingkey.getStatus() == IdempotencyKey.Status.SUCCEEDED) {
                return existingkey.getTransferId();
            }

            throw new IllegalStateException("Transaction is already being processed");
        }

        IdempotencyKey key = new IdempotencyKey(idempotencyKey);
        idempotencyKeyRepository.saveAndFlush(key);

        // 3. Deadlock prevention (lowest first)
        Long firstId = Math.min(fromAccountId, toAccountId);
        Long secondId = Math.max(fromAccountId, toAccountId);

        // 4. Acquire Pessimistic Locks in Sorted Order
        Account first = accountRepository.findByIdForUpdate(firstId)
        .orElseThrow(() -> new IllegalArgumentException("Account not found: " + firstId));
        
        Account second = accountRepository.findByIdForUpdate(secondId)
        .orElseThrow(() -> new IllegalArgumentException("Account not found: " + secondId));


        // 5. Re-assign references after locking
        Account sender = fromAccountId.equals(firstId) ? first : second;
        Account receiver = fromAccountId.equals(firstId) ? second : first;

        // 6. Balance Check
        if (sender.getBalance().compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient balance in account " + sender.getId());
        }

        // 7. Move Money in Memory
        sender.setBalance(sender.getBalance().subtract(amount));
        receiver.setBalance(receiver.getBalance().add(amount));


        // 8. Write the two double-entry rows
        UUID transferId = UUID.randomUUID();
        LedgerEntry debit = new LedgerEntry(fromAccountId, amount.negate(), transferId);
        LedgerEntry credit = new LedgerEntry(toAccountId, amount, transferId);

        // 9. Persist and resolve state
        ledgerEntryRepository.saveAll(List.of(debit, credit));

        key.setStatus(IdempotencyKey.Status.SUCCEEDED);
        key.setTransferId(transferId);

        return transferId;
    }
}
