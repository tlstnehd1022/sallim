CREATE TABLE chore_completion_record (
    id CHAR(36) NOT NULL,
    chore_instance_id CHAR(36) NOT NULL,
    chore_definition_id CHAR(36) NOT NULL,
    completed_by CHAR(36) NOT NULL,
    completed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (chore_instance_id)
);
