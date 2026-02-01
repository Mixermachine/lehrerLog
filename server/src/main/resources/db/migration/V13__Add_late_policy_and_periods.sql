CREATE TABLE teacher_late_policy
(
    teacher_id UUID PRIMARY KEY REFERENCES users (id),
    threshold  INTEGER NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE late_periods
(
    id         UUID PRIMARY KEY,
    teacher_id UUID                     NOT NULL REFERENCES users (id),
    name       VARCHAR(200)             NOT NULL,
    starts_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    ends_at    TIMESTAMP WITH TIME ZONE,
    is_active  BOOLEAN                  NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE          DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE student_late_stats
(
    teacher_id          UUID    NOT NULL REFERENCES users (id),
    student_id          UUID    NOT NULL REFERENCES students (id),
    period_id           UUID    NOT NULL REFERENCES late_periods (id),
    total_missed        INTEGER NOT NULL,
    missed_since_punishment INTEGER NOT NULL,
    punishment_required BOOLEAN NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (teacher_id, student_id, period_id)
);

CREATE TABLE punishment_records
(
    id          UUID PRIMARY KEY,
    teacher_id  UUID NOT NULL REFERENCES users (id),
    student_id  UUID NOT NULL REFERENCES students (id),
    period_id   UUID NOT NULL REFERENCES late_periods (id),
    triggered_at TIMESTAMP WITH TIME ZONE NOT NULL,
    resolved_at TIMESTAMP WITH TIME ZONE,
    resolved_by UUID REFERENCES users (id),
    note        TEXT,
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE task_submissions
    ADD COLUMN late_status VARCHAR(32) NOT NULL DEFAULT 'ON_TIME';
ALTER TABLE task_submissions
    ADD COLUMN late_period_id UUID;
ALTER TABLE task_submissions
    ADD COLUMN decided_by UUID;
ALTER TABLE task_submissions
    ADD COLUMN decided_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE task_submissions
    ADD CONSTRAINT fk_task_submissions_late_period
        FOREIGN KEY (late_period_id) REFERENCES late_periods (id);
ALTER TABLE task_submissions
    ADD CONSTRAINT fk_task_submissions_decided_by
        FOREIGN KEY (decided_by) REFERENCES users (id);
