package com.noctivagous.ledgerpay.service;

import com.noctivagous.ledgerpay.AbstractIntegrationTest;
import com.noctivagous.ledgerpay.domain.Account;
import com.noctivagous.ledgerpay.domain.IdempotencyKey;
import com.noctivagous.ledgerpay.domain.LedgerEntry;
import com.noctivagous.ledgerpay.exception.InsufficientFundsException;
import com.noctivagous.ledgerpay.exception.TransferInProgressException;
import com.noctivagous.ledgerpay.repository.AccountRepository;
import com.noctivagous.ledgerpay.repository.IdempotencyKeyRepository;
import com.noctivagous.ledgerpay.repository.LedgerEntryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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

    @Test
    void concurrentTransfersInBothDirections_noDeadlockNoLostUpdates() throws Exception {
        // A -> B and B -> A at the same time would deadlock without ordered locking
        List<Callable<UUID>> tasks = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            tasks.add(() -> transferService.transfer(accountAId, accountBId, new BigDecimal("10.00"), UUID.randomUUID().toString()));
            tasks.add(() -> transferService.transfer(accountBId, accountAId, new BigDecimal("5.00"), UUID.randomUUID().toString()));
        }

        for (Future<UUID> result : runConcurrently(tasks)) {
            assertNotNull(result.get());
        }

        assertBalance(accountAId, "800.00"); // 1000 - 40 * 10 + 40 * 5
        assertBalance(accountBId, "700.00"); //  500 + 40 * 10 - 40 * 5
        assertEquals(0, new BigDecimal("-200.00").compareTo(sumOfEntries(accountAId)));
        assertEquals(0, new BigDecimal("200.00").compareTo(sumOfEntries(accountBId)));
    }

    @Test
    void concurrentOverdraftAttempts_neverGoBelowZero() throws Exception {
        // A holds 1000.00, so only 10 of these 20 transfers can succeed
        List<Callable<UUID>> tasks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            tasks.add(() -> transferService.transfer(accountAId, accountBId, new BigDecimal("100.00"), UUID.randomUUID().toString()));
        }

        int succeeded = 0;
        for (Future<UUID> result : runConcurrently(tasks)) {
            try {
                result.get();
                succeeded++;
            } catch (ExecutionException e) {
                assertInstanceOf(InsufficientFundsException.class, e.getCause());
            }
        }

        assertEquals(10, succeeded);
        assertBalance(accountAId, "0.00");
        assertBalance(accountBId, "1500.00");
    }

    @Test
    void concurrentRequestsWithSameKey_transferOnlyOnce() throws Exception {
        String key = UUID.randomUUID().toString();
        List<Callable<UUID>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tasks.add(() -> transferService.transfer(accountAId, accountBId, new BigDecimal("100.00"), key));
        }

        Set<UUID> transferIds = new HashSet<>();
        for (Future<UUID> result : runConcurrently(tasks)) {
            try {
                transferIds.add(result.get());
            } catch (ExecutionException e) {
                assertInstanceOf(TransferInProgressException.class, e.getCause());
            }
        }

        assertEquals(1, transferIds.size());
        assertBalance(accountAId, "900.00");
        assertBalance(accountBId, "600.00");
        assertEquals(2, ledgerEntryRepository.findByTransferId(transferIds.iterator().next()).size());
    }

    @Test
    void retryWithSameKey_returnsOriginalTransfer() {
        String key = UUID.randomUUID().toString();

        UUID first = transferService.transfer(accountAId, accountBId, new BigDecimal("200.00"), key);
        UUID retry = transferService.transfer(accountAId, accountBId, new BigDecimal("200.00"), key);

        assertEquals(first, retry);
        assertBalance(accountAId, "800.00");
        assertBalance(accountBId, "700.00");
    }

    @Test
    void failedTransfer_rollsBackAndReleasesKey() {
        String key = UUID.randomUUID().toString();

        assertThrows(InsufficientFundsException.class,
                () -> transferService.transfer(accountAId, accountBId, new BigDecimal("5000.00"), key));

        assertBalance(accountAId, "1000.00");
        assertBalance(accountBId, "500.00");
        assertTrue(ledgerEntryRepository.findByAccountIdOrderByIdAsc(accountAId).isEmpty());
        assertTrue(idempotencyKeyRepository.findById(key).isEmpty());
    }

    @Test
    void deposit_isBalancedByClearingAccount() {
        BigDecimal clearingBefore = balanceOf(Account.CLEARING_ACCOUNT_ID);

        UUID transferId = transferService.deposit(accountAId, new BigDecimal("250.00"), UUID.randomUUID().toString());

        assertBalance(accountAId, "1250.00");
        assertEquals(0, clearingBefore.subtract(new BigDecimal("250.00")).compareTo(balanceOf(Account.CLEARING_ACCOUNT_ID)));
        BigDecimal net = ledgerEntryRepository.findByTransferId(transferId).stream()
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, net.signum());
    }

    @Test
    void transferFromClearingAccount_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> transferService.transfer(
                Account.CLEARING_ACCOUNT_ID, accountAId, new BigDecimal("1.00"), UUID.randomUUID().toString()));
    }

    private <T> List<Future<T>> runConcurrently(List<Callable<T>> tasks) throws InterruptedException {
        try (ExecutorService pool = Executors.newFixedThreadPool(16)) {
            return pool.invokeAll(tasks);
        }
    }

    private BigDecimal balanceOf(Long accountId) {
        return accountRepository.findById(accountId).orElseThrow().getBalance();
    }

    private void assertBalance(Long accountId, String expected) {
        BigDecimal actual = balanceOf(accountId);
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "account " + accountId + " balance is " + actual);
    }

    private BigDecimal sumOfEntries(Long accountId) {
        return ledgerEntryRepository.findByAccountIdOrderByIdAsc(accountId).stream()
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
