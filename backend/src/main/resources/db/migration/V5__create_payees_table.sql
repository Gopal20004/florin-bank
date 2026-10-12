-- Saved recipients. A payee points at a Florin account, so its holder name is always the verified one.
CREATE TABLE payees (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    nickname VARCHAR(40) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_payees_customer_account UNIQUE (customer_id, account_id),
    CONSTRAINT fk_payees_customer FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE CASCADE,
    CONSTRAINT fk_payees_account FOREIGN KEY (account_id) REFERENCES accounts(id)
);
