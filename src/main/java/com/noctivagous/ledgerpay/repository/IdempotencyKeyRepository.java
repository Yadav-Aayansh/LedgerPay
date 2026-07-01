package com.noctivagous.ledgerpay.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.noctivagous.ledgerpay.domain.IdempotencyKey;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, String> {
    
}
