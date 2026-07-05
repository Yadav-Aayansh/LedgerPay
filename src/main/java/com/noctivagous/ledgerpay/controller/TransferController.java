package com.noctivagous.ledgerpay.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.noctivagous.ledgerpay.dto.TransferRequest;
import com.noctivagous.ledgerpay.dto.TransferResponse;
import com.noctivagous.ledgerpay.service.TransferService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransferResponse transfer(@RequestHeader("Idempotency-Key") @NotBlank @Size(max = 255) String idempotencyKey,
                                     @Valid @RequestBody TransferRequest request) {
        UUID transferId = transferService.transfer(request.fromAccountId(), request.toAccountId(), request.amount(), idempotencyKey);
        return new TransferResponse(transferId);
    }
}
