CREATE TABLE task_targets
(
    task_id    UUID NOT NULL REFERENCES tasks (id) ON DELETE CASCADE,
    student_id UUID NOT NULL REFERENCES students (id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (task_id, student_id)
);

CREATE INDEX idx_task_targets_task_id ON task_targets (task_id);
CREATE INDEX idx_task_targets_student_id ON task_targets (student_id);

INSERT INTO task_targets (task_id, student_id, created_at)
SELECT DISTINCT t.id, sc.student_id, CURRENT_TIMESTAMP
FROM tasks t
         JOIN student_classes sc ON sc.school_class_id = t.school_class_id
WHERE sc.valid_till IS NULL
   OR sc.valid_till > CURRENT_TIMESTAMP;

ALTER TABLE task_submissions
    ADD COLUMN id UUID;
UPDATE task_submissions
SET id = gen_random_uuid()
WHERE id IS NULL;
ALTER TABLE task_submissions
    ALTER COLUMN id SET NOT NULL;

ALTER TABLE task_submissions
    ADD CONSTRAINT task_submissions_id_unique UNIQUE (id);

ALTER TABLE task_submissions
    ADD COLUMN submission_type TEXT NOT NULL DEFAULT 'FILE';
ALTER TABLE task_submissions
    ADD COLUMN grade NUMERIC(5, 2);
ALTER TABLE task_submissions
    ADD COLUMN note TEXT;
ALTER TABLE task_submissions
    ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE task_submissions
    ADD COLUMN version BIGINT NOT NULL DEFAULT 1;

ALTER TABLE task_submissions
    ADD CONSTRAINT task_submissions_task_student_unique UNIQUE (task_id, student_id);

ALTER TABLE task_submissions
    ADD CONSTRAINT task_submissions_submission_type_check
        CHECK (submission_type IN ('FILE', 'IN_PERSON'));
