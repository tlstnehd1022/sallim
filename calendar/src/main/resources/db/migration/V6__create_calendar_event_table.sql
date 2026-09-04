CREATE TABLE calendar_event (
    id CHAR(36) NOT NULL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    start_at DATETIME(6) NOT NULL,
    member_id CHAR(36) NOT NULL,
    memo TEXT,
    recurrence_type VARCHAR(20),
    recurrence_times INT
);
