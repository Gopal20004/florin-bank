-- One row per money movement as the customer sees it: a deposit, a withdrawal or a transfer.
CREATE TABLE transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    reference CHAR(13) NOT NULL,
    type VARCHAR(12) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    currency CHAR(3) NOT NULL,
    description VARCHAR(140) NULL,
    initiated_by BIGINT NOT NULL,
    -- A retried request with the same Idempotency-Key returns this transaction instead of moving money twice.
    idempotency_key VARCHAR(64) NULL,
    request_hash CHAR(64) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_transactions_reference UNIQUE (reference),
    -- NULL keys never collide in a MySQL unique index, so requests without a key are unaffected.
    CONSTRAINT uq_transactions_idempotency UNIQUE (initiated_by, idempotency_key),
    CONSTRAINT chk_transactions_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_transactions_type CHECK (type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER')),
    CONSTRAINT fk_transactions_initiator FOREIGN KEY (initiated_by) REFERENCES customers(id)
);

-- The legs of a transaction: one per account it touched. A transfer has a DEBIT and a CREDIT of equal
-- amount, written in the same database transaction as both balance updates.
CREATE TABLE ledger_entries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    direction VARCHAR(6) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    balance_after DECIMAL(19, 2) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_ledger_transaction_account UNIQUE (transaction_id, account_id),
    CONSTRAINT chk_ledger_direction CHECK (direction IN ('CREDIT', 'DEBIT')),
    CONSTRAINT chk_ledger_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_ledger_balance_non_negative CHECK (balance_after >= 0),
    CONSTRAINT fk_ledger_transaction FOREIGN KEY (transaction_id) REFERENCES transactions(id),
    CONSTRAINT fk_ledger_account FOREIGN KEY (account_id) REFERENCES accounts(id)
);

-- Statements read one account newest first; the id breaks ties between entries in the same microsecond.
CREATE INDEX idx_ledger_account_created ON ledger_entries(account_id, created_at, id);
