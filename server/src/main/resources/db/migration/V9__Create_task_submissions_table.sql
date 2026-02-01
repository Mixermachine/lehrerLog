CREATE TABLE task_submissions
(
    task_id    UUID                     NOT NULL REFERENCES tasks (id) ON DELETE CASCADE,
    student_id UUID                     NOT NULL REFERENCES students (id) ON DELETE CASCADE,
    submitted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (task_id, student_id)
);

CREATE INDEX idx_task_submissions_task_id ON task_submissions (task_id);
CREATE INDEX idx_task_submissions_student_id ON task_submissions (student_id);
