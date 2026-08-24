ALTER TABLE chore_instance
    ADD CONSTRAINT uq_chore_instance_definition_date UNIQUE (chore_definition_id, scheduled_date);
