package com.noctivagous.ledgerpay.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.noctivagous.ledgerpay.domain.Account;

public record AccountResponse(Long id, Long ownerId, BigDecimal balance, Instant createdAt) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(account.getId(), account.getOwnerId(), account.getBalance(), account.getCreatedAt());
    }
}
