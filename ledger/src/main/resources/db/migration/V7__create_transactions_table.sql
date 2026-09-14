CREATE TABLE transactions (
    id CHAR(36) NOT NULL PRIMARY KEY,
    member_id CHAR(36) NOT NULL,
    amount BIGINT NOT NULL,
    category VARCHAR(255) NOT NULL,
    memo TEXT,
    occurred_at DATETIME(6) NOT NULL
);
