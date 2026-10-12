-- Only a SHA-256 of each bearer token is stored, so a copy of this table cannot be used to sign in.
CREATE TABLE auth_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    token_hash CHAR(64) NOT NULL,
    user_agent VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at DATETIME(6) NOT NULL,
    CONSTRAINT uq_auth_sessions_token UNIQUE (token_hash),
    CONSTRAINT fk_auth_sessions_customer FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE CASCADE
);

CREATE INDEX idx_auth_sessions_expires_at ON auth_sessions(expires_at);
CREATE INDEX idx_auth_sessions_customer ON auth_sessions(customer_id);
