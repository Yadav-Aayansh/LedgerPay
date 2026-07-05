package com.noctivagous.ledgerpay.controller;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.noctivagous.ledgerpay.domain.Account;
import com.noctivagous.ledgerpay.dto.AccountResponse;
import com.noctivagous.ledgerpay.dto.CreateAccountRequest;
import com.noctivagous.ledgerpay.dto.DepositRequest;
import com.noctivagous.ledgerpay.dto.LedgerEntryResponse;
import com.noctivagous.ledgerpay.dto.TransferResponse;
import com.noctivagous.ledgerpay.exception.AccountNotFoundException;
import com.noctivagous.ledgerpay.repository.AccountRepository;
import com.noctivagous.ledgerpay.repository.LedgerEntryRepository;
import com.noctivagous.ledgerpay.service.TransferService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final TransferService transferService;

    public AccountController(AccountRepository accountRepository, LedgerEntryRepository ledgerEntryRepository, TransferService transferService) {
        this.accountRepository = accountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.transferService = transferService;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request) {
        Account account = accountRepository.save(new Account(request.ownerId(), BigDecimal.ZERO));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(account.getId()).toUri();
        return ResponseEntity.created(location).body(AccountResponse.from(account));
    }

    @GetMapping
    public List<AccountResponse> listByOwner(@RequestParam Long ownerId) {
        return accountRepository.findByOwnerId(ownerId).stream().map(AccountResponse::from).toList();
    }

    @GetMapping("/{id}")
    public AccountResponse get(@PathVariable Long id) {
        return accountRepository.findById(id)
                .map(AccountResponse::from)
                .orElseThrow(() -> new AccountNotFoundException(id));
    }

    @GetMapping("/{id}/entries")
    public List<LedgerEntryResponse> entries(@PathVariable Long id) {
        if (!accountRepository.existsById(id)) {
            throw new AccountNotFoundException(id);
        }
        return ledgerEntryRepository.findByAccountIdOrderByIdAsc(id).stream().map(LedgerEntryResponse::from).toList();
    }

    @PostMapping("/{id}/deposits")
    @ResponseStatus(HttpStatus.CREATED)
    public TransferResponse deposit(@PathVariable Long id,
                                    @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 255) String idempotencyKey,
                                    @Valid @RequestBody DepositRequest request) {
        return new TransferResponse(transferService.deposit(id, request.amount(), idempotencyKey));
    }
}
