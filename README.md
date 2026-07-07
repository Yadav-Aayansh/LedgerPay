# LedgerPay

Payment ledger service built with Spring Boot 4, Java 21 and PostgreSQL. It moves money between accounts using double-entry bookkeeping and stays correct under concurrent requests.

## How it works

- Every money movement writes two `ledger_entry` rows with the same `transfer_id`: a debit (negative) and a credit (positive). Deposits are debited from a system clearing account (id `0`), so all balances always sum to zero.
- A transfer locks both accounts with `SELECT ... FOR UPDATE` before checking the balance, so concurrent transfers can't overdraw an account or lose an update.
- Locks are always taken in ascending account id order, so A to B and B to A running at the same time can't deadlock.
- Writes need an `Idempotency-Key` header. The key is inserted in the same transaction as the transfer. Retrying a completed request returns the original `transferId`, a duplicate sent while the first is still running gets `409`, and a failed transfer rolls back its key so it can be retried.
- Flyway owns the schema and Hibernate only validates it. Amounts are `NUMERIC(19,4)`, and a `CHECK` constraint stops every account except the clearing account from going negative.

## Running locally

Requires Java 21 and Docker.

```bash
docker compose up -d
./mvnw spring-boot:run
```

The API uses HTTP Basic auth with `ledgerpay` / `ledgerpay` by default (override with `SPRING_SECURITY_USER_NAME` and `SPRING_SECURITY_USER_PASSWORD`).

```bash
# open two accounts (ids 1 and 2 on a fresh database)
curl -u ledgerpay:ledgerpay -H 'Content-Type: application/json' -d '{"ownerId": 1}' localhost:8080/api/accounts
curl -u ledgerpay:ledgerpay -H 'Content-Type: application/json' -d '{"ownerId": 2}' localhost:8080/api/accounts

# deposit 500 into account 1
curl -u ledgerpay:ledgerpay -H 'Content-Type: application/json' -H 'Idempotency-Key: deposit-1' \
  -d '{"amount": 500}' localhost:8080/api/accounts/1/deposits

# transfer 120 from account 1 to account 2
curl -u ledgerpay:ledgerpay -H 'Content-Type: application/json' -H 'Idempotency-Key: transfer-1' \
  -d '{"fromAccountId": 1, "toAccountId": 2, "amount": 120}' localhost:8080/api/transfers

# ledger entries of account 1
curl -u ledgerpay:ledgerpay localhost:8080/api/accounts/1/entries
```

## API

| Method | Path | Body | Notes |
|---|---|---|---|
| `POST` | `/api/accounts` | `{"ownerId"}` | Opens an account with a zero balance |
| `GET` | `/api/accounts?ownerId={ownerId}` | | Accounts of an owner |
| `GET` | `/api/accounts/{id}` | | Account and balance |
| `GET` | `/api/accounts/{id}/entries` | | Ledger entries, oldest first |
| `POST` | `/api/accounts/{id}/deposits` | `{"amount"}` | Needs `Idempotency-Key` |
| `POST` | `/api/transfers` | `{"fromAccountId", "toAccountId", "amount"}` | Needs `Idempotency-Key` |

Errors are returned as RFC 9457 problem details: `400` invalid request, `401` missing credentials, `404` unknown account, `409` same key already in progress, `422` insufficient funds.

## Tests

```bash
./mvnw test
```

The integration tests run against PostgreSQL in Testcontainers, so Docker has to be running. They cover concurrent transfers in both directions, concurrent overdraft attempts, concurrent requests with the same idempotency key, rollback on failure, and the HTTP layer.
