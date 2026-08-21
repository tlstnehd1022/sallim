CREATE TABLE chore_instance (
    id CHAR(36) NOT NULL,
    chore_definition_id CHAR(36) NOT NULL,
    scheduled_date DATE NOT NULL,
    completed BOOLEAN NOT NULL,
    completed_by CHAR(36) NULL,
    completed_at DATETIME(6) NULL,
    PRIMARY KEY (id)
);
