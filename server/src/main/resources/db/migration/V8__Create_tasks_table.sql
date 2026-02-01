CREATE TABLE tasks
(
    id              UUID                              DEFAULT gen_random_uuid() PRIMARY KEY,
    school_id       UUID                     NOT NULL REFERENCES schools (id) ON DELETE CASCADE,
    school_class_id UUID                     NOT NULL REFERENCES school_classes (id) ON DELETE CASCADE,
    title           VARCHAR(200)             NOT NULL,
    description     TEXT,
    due_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by      UUID                     NOT NULL REFERENCES users (id),
    version         BIGINT                   NOT NULL DEFAULT 1,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_tasks_school_id ON tasks (school_id);
CREATE INDEX idx_tasks_school_class_id ON tasks (school_class_id);

