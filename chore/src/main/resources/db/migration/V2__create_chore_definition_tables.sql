CREATE TABLE chore_definition (
    id CHAR(36) NOT NULL,
    room_id CHAR(36) NOT NULL,
    label VARCHAR(255) NOT NULL,
    assignee_id CHAR(36) NOT NULL,
    recurrence_type VARCHAR(32) NOT NULL,
    recurrence_times INT NULL,
    video_query VARCHAR(255) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE chore_definition_step (
    chore_definition_id CHAR(36) NOT NULL,
    step_order INT NOT NULL,
    step TEXT NOT NULL,
    PRIMARY KEY (chore_definition_id, step_order)
);
