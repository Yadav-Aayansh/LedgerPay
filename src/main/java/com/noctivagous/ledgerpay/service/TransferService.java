package com.noctivagous.ledgerpay.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.noctivagous.ledgerpay.domain.Account;
import com.noctivagous.ledgerpay.domain.IdempotencyKey;
import com.noctivagous.ledgerpay.domain.LedgerEntry;
import com.noctivagous.ledgerpay.exception.AccountNotFoundException;
import com.noctivagous.ledgerpay.exception.InsufficientFundsException;
import com.noctivagous.ledgerpay.exception.TransferInProgressException;
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
        if (Account.CLEARING_ACCOUNT_ID.equals(fromAccountId) || Account.CLEARING_ACCOUNT_ID.equals(toAccountId)) {
            throw new IllegalArgumentException("The clearing account cannot be used in transfers");
        }
        return move(fromAccountId, toAccountId, amount, idempotencyKey);
    }

    // Deposits are debited from the clearing account, so they are balanced transfers too
    @Transactional
    public UUID deposit(Long accountId, BigDecimal amount, String idempotencyKey) {
        return move(Account.CLEARING_ACCOUNT_ID, accountId, amount, idempotencyKey);
    }

    private UUID move(Long fromAccountId, Long toAccountId, BigDecimal amount, String idempotencyKey) {
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

            throw new TransferInProgressException(idempotencyKey);
        }

        IdempotencyKey key = new IdempotencyKey(idempotencyKey);
        try {
            key = idempotencyKeyRepository.saveAndFlush(key);
        } catch (DataIntegrityViolationException e) {
            // A concurrent request inserted the same key first. A retry returns its result.
            throw new TransferInProgressException(idempotencyKey);
        }

        // 3. Deadlock prevention (lowest first)
        Long firstId = Math.min(fromAccountId, toAccountId);
        Long secondId = Math.max(fromAccountId, toAccountId);

        // 4. Acquire Pessimistic Locks in Sorted Order
        Account first = accountRepository.findByIdForUpdate(firstId)
        .orElseThrow(() -> new AccountNotFoundException(firstId));

        Account second = accountRepository.findByIdForUpdate(secondId)
        .orElseThrow(() -> new AccountNotFoundException(secondId));


        // 5. Re-assign references after locking
        Account sender = fromAccountId.equals(firstId) ? first : second;
        Account receiver = fromAccountId.equals(firstId) ? second : first;

        // 6. Balance Check (only the clearing account may go negative)
        if (!Account.CLEARING_ACCOUNT_ID.equals(sender.getId()) && sender.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException(sender.getId());
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
