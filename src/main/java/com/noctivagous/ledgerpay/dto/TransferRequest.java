package com.noctivagous.ledgerpay.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TransferRequest(
        @NotNull Long fromAccountId,
        @NotNull Long toAccountId,
        // Same precision as NUMERIC(19,4), so the database never rounds an amount
        @NotNull @Positive @Digits(integer = 15, fraction = 4) BigDecimal amount) {
}
