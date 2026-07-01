package com.noctivagous.ledgerpay.repository;

import com.noctivagous.ledgerpay.domain.Account;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


public interface AccountRepository extends JpaRepository<Account, Long> {

    List<Account> findByOwnerId(Long ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("Select a FROM Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") Long id);

}
