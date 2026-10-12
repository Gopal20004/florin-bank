-- Money is DECIMAL(19,2), never FLOAT/DOUBLE: rupees and paise are stored exactly.
CREATE TABLE accounts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    account_number CHAR(12) NOT NULL,
    type VARCHAR(10) NOT NULL,
    nickname VARCHAR(40) NOT NULL,
    balance DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    currency CHAR(3) NOT NULL DEFAULT 'INR',
    status VARCHAR(10) NOT NULL,
    opened_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    closed_at DATETIME(6) NULL,
    CONSTRAINT uq_accounts_number UNIQUE (account_number),
    -- The last line of defence against an overdraft: even a buggy code path cannot commit a negative balance.
    CONSTRAINT chk_accounts_balance_non_negative CHECK (balance >= 0),
    CONSTRAINT chk_accounts_type CHECK (type IN ('SAVINGS', 'CURRENT')),
    CONSTRAINT chk_accounts_status CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    CONSTRAINT chk_accounts_closed_empty CHECK (status <> 'CLOSED' OR balance = 0),
    -- No cascade: an account with history must never disappear with its owner.
    CONSTRAINT fk_accounts_customer FOREIGN KEY (customer_id) REFERENCES customers(id)
);

CREATE INDEX idx_accounts_customer ON accounts(customer_id);
