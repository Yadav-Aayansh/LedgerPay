package com.noctivagous.ledgerpay.dto;

import jakarta.validation.constraints.NotNull;

public record CreateAccountRequest(@NotNull Long ownerId) {
}
