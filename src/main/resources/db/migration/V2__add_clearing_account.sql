-- System clearing account, the counterparty of every deposit. Id 0 is outside the identity range.
-- Its balance goes negative by the total deposited, so all balances always sum to zero.
INSERT INTO account (id, owner_id, balance, version, created_at)
VALUES (0, 0, 0, 0, now());

ALTER TABLE account
    ADD CONSTRAINT chk_account_balance_non_negative CHECK (balance >= 0 OR id = 0);
