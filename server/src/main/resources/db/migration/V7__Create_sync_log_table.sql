CREATE TABLE sync_log
(
    id          BIGSERIAL PRIMARY KEY,
    school_id   UUID                     NOT NULL REFERENCES schools (id) ON DELETE CASCADE,
    entity_type VARCHAR(50)              NOT NULL,
    entity_id   UUID                     NOT NULL,
    operation   VARCHAR(20)              NOT NULL,
    user_id     UUID                     NOT NULL REFERENCES users (id),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_sync_log_school_id ON sync_log (school_id);
CREATE INDEX idx_sync_log_created_at ON sync_log (created_at);
CREATE INDEX idx_sync_log_school_entity ON sync_log (school_id, entity_type, created_at);
